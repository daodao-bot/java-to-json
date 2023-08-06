package com.github.daodao.tojson.process;

import com.github.daodao.tojson.annotation.ToJson;
import com.google.auto.service.AutoService;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 使用 javax.annotation.processing.AbstractProcessor 在编译期补充一个 toJson 方法
 * 新建 resources/META-INF/services/javax.annotation.processing.Processor 文件， 内容为 AbstractProcessor 的继承类的全路径名
 */
@SupportedAnnotationTypes({"com.github.daodao.tojson.annotation.ToJson"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class ToJsonProcessor extends AbstractProcessor {

    private static final String METHOD_NAME = "toJson";

    private Messager messager;

    private Set<TypeElement> doneElements;

    private JavacTrees trees;

    private TreeMaker treeMaker;

    private Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {

        processingEnv = jbUnwrap(ProcessingEnvironment.class, processingEnv);

        super.init(processingEnv);

        this.messager = processingEnv.getMessager();

        this.messager.printMessage(Diagnostic.Kind.NOTE, this.getClass().getSimpleName() + " init ...");

        this.doneElements = new HashSet<>();


        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        messager.printMessage(Diagnostic.Kind.NOTE, this.getClass().getSimpleName() + " process ...");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(ToJson.class);

        for (Element element : elements) {

            if (!element.getKind().isClass()) {
                continue;
            }

            TypeElement typeElement = (TypeElement) element;

            if (doneElements.contains(typeElement)) {
                continue;
            }

            if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            if (!hasPublicConstructor(typeElement)) {
                continue;
            }

            if (!hasToJsonMethod(typeElement)) {

                JCTree jcTree = trees.getTree(typeElement);
                jcTree.accept(new TreeTranslator() {

                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {

                        jcClassDecl.defs = jcClassDecl.defs.append(toJsonMethodDecl());
                        super.visitClassDef(jcClassDecl);

                    }

                });

            }

            doneElements.add(typeElement);

        }

        return true;
    }

    private JCTree.JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, names.fromString(componentArray[i]));
        }
        return expr;
    }

    private JCTree.JCMethodDecl toJsonMethodDecl() {

        /*
         * 修饰符
         */
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC, List.nil());

        /*
         * 返回类型
         */
        JCTree.JCExpression returnType = memberAccess("java.lang.String");

        /*
         * 方法名
         */
        Name name = names.fromString(METHOD_NAME);

        /*
         * 泛型参数
         */
        List<JCTree.JCTypeParameter> typeParameters = List.nil();

        /*
         * 参数
         */
        List<JCTree.JCVariableDecl> parameters = List.nil();

        /*
         * 抛出异常
         */
        List<JCTree.JCExpression> throwsClauses = List.nil();

        /*
         * try 语句
         */
        JCTree.JCTry jcTry = toJsonTry();
        List<JCTree.JCStatement> jcStatements = List.of(jcTry);

        /*
         * 方法体
         */
        JCTree.JCBlock jcBlock = treeMaker.Block(0, jcStatements);

        /*
         * 默认值
         */
        JCTree.JCVariableDecl variableDecl = null;

        return treeMaker.MethodDef(modifiers, name, returnType, typeParameters, variableDecl, parameters, throwsClauses, jcBlock, null);
    }

    private List<JCTree.JCStatement> toJsonStatement() {

        JCTree.JCVariableDecl variableDecl = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("objectMapper"),
                memberAccess("com.fasterxml.jackson.databind.ObjectMapper"),
                treeMaker.NewClass(
                        null,
                        List.nil(),
                        memberAccess("com.fasterxml.jackson.databind.ObjectMapper"),
                        List.nil(),
                        null
                )
        );

        JCTree.JCExpressionStatement jcExpressionStatement = treeMaker.Exec(treeMaker.Apply(
                List.of(memberAccess("java.lang.Object")),
                memberAccess("objectMapper.writeValueAsString"),
                List.of(treeMaker.Ident(names.fromString("this")))
        ));
        JCTree.JCExpression jcExpression = jcExpressionStatement.getExpression();

        ListBuffer<JCTree.JCStatement> jcStatements = new ListBuffer<>();
        jcStatements.append(variableDecl);
        jcStatements.append(treeMaker.Return(jcExpression));

        return jcStatements.toList();
    }

    private JCTree.JCTry toJsonTry() {
        List<JCTree.JCStatement> toJsonStatement = toJsonStatement();
        JCTree.JCBlock tryBlock = treeMaker.Block(0, toJsonStatement);
        JCTree.JCCatch catchBlock = toJsonCatch();
        return treeMaker.Try(tryBlock, List.of(catchBlock), null);
    }

    private JCTree.JCCatch toJsonCatch() {
        JCTree.JCVariableDecl param = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("e"),
                memberAccess("com.fasterxml.jackson.core.JsonProcessingException"),
                null
        );
        JCTree.JCBlock catchBlock = treeMaker.Block(0, List.of(toJsonThrow()));
        return treeMaker.Catch(param, catchBlock);
    }

    private JCTree.JCThrow toJsonThrow() {
        JCTree.JCVariableDecl variableDecl = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("ex"),
                memberAccess("java.lang.RuntimeException"),
                treeMaker.NewClass(
                        null,
                        List.nil(),
                        memberAccess("java.lang.RuntimeException"),
                        List.of(treeMaker.Ident(names.fromString("e"))),
                        null
                )
        );
        return treeMaker.Throw(variableDecl.getInitializer());
    }

    private boolean hasToJsonMethod(TypeElement typeElement) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (ElementKind.METHOD != enclosedElement.getKind()) {
                continue;
            }
            ExecutableElement executableElement = (ExecutableElement) enclosedElement;
            if (!executableElement.getSimpleName().toString().equals(METHOD_NAME)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasPublicConstructor(TypeElement typeElement) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (ElementKind.CONSTRUCTOR.equals(enclosedElement.getKind())) {
                ExecutableElement constructorElement = (ExecutableElement) enclosedElement;
                if (constructorElement.getParameters().isEmpty() && constructorElement.getModifiers().contains(Modifier.PUBLIC)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
        T unwrapped = null;
        try {
            final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            final Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
        } catch (Throwable ignored) {
        }
        return unwrapped != null ? unwrapped : wrapper;
    }

}
