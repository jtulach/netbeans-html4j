/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.html.react;

import net.java.html.react.RegisterComponent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.lookup.ServiceProvider;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import net.java.html.react.Render;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

import static org.netbeans.html.react.JavaSxProcessor.Token.Type.TEXT;

@org.apidesign.bck2brwsr.core.ExtraJavaScript(processByteCode = false, resource = "")
@ServiceProvider(service = Processor.class)
public class JavaSxProcessor extends AbstractProcessor {
    private static final String EXP_ERR_NAME = "net.java.html.react.test.ExpectedError";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> names = new HashSet<>();
        names.add(Render.class.getCanonicalName());
        return names;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private static final class ParamCompletion implements Completion {
        private final String prefix;
        private final Name name;

        ParamCompletion(String prefix, Name name) {
            this.prefix = prefix;
            this.name = name;
        }

        @Override
        public String getValue() {
            return prefix + name + "}";
        }

        @Override
        public String getMessage() {
            return "Access parameter " + name;
        }
    }

    private static final class ThisCompletion implements Completion {
        private final String prefix;

        public ThisCompletion(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String getValue() {
            return prefix + "this.";
        }

        @Override
        public String getMessage() {
            return "Call this method";
        }
    }

    private static final class CallCompletion implements Completion {
        private final String prefix;
        private final Name name;
        private final List<? extends VariableElement> types;

        CallCompletion(String prefix, Name name, List<? extends VariableElement> parameters) {
            this.prefix = prefix;
            this.name = name;
            this.types = parameters;
        }

        @Override
        public String getValue() {
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append(name).append("(");
            String sep = "";
            for (int i = 0; i < types.size(); i++) {
                sb.append(sep);
                switch (types.get(i).asType().getKind()) {
                    case BOOLEAN:
                        sb.append("false");
                        break;
                    case BYTE:
                    case CHAR:
                    case SHORT:
                    case INT:
                    case LONG:
                        sb.append("0");
                        break;
                    case FLOAT:
                    case DOUBLE:
                        sb.append("0.0");
                        break;
                    default:
                        sb.append("null");
                }
                sep = ", ";
            }
            sb.append(")}");
            return sb.toString();
        }

        @Override
        public String getMessage() {
            return "Call " + name;
        }
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror mirror, ExecutableElement member, String userText) {
        List<Completion> arr = new ArrayList<>();
        if (element.getKind() != ElementKind.METHOD) {
            return arr;
        }
        if (userText.endsWith("{") && element.getKind() == ElementKind.METHOD) {
            ExecutableElement ee = (ExecutableElement) element;
            for (VariableElement p : ee.getParameters()) {
                arr.add(new ParamCompletion(userText, p.getSimpleName()));
            }
            arr.add(new ThisCompletion(userText));
            return arr;
        } else if (userText.endsWith("{this.")) {
            for (Element e : element.getEnclosingElement().getEnclosedElements()) {
                if (e.getKind() != ElementKind.METHOD || e == element) {
                    continue;
                }
                ExecutableElement ee = (ExecutableElement) e;
                if (isReactElement(ee.getReturnType())) {
                    arr.add(new CallCompletion(userText, ee.getSimpleName(), ee.getParameters()));
                }
            }
        }
        return arr;
    }

    public @Override boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Set<Element> expectedErrors = new HashSet<>();

        Map<TypeElement,Set<ExecutableElement>> annotatedElementsByClass = new HashMap<>();
        for (Element e : roundEnv.getElementsAnnotatedWith(Render.class)) {
            if (e.getKind() != ElementKind.METHOD) {
                continue;
            }
            final Element clazz = e.getEnclosingElement();
            if (clazz.getKind() != ElementKind.CLASS || clazz.getAnnotation(RegisterComponent.class) == null) {
                emitError(e, expectedErrors, "@Render in a class without @RegisterComponent");
                continue;
            }
            if (
                !e.getModifiers().contains(Modifier.ABSTRACT) ||
                !e.getModifiers().contains(Modifier.PROTECTED)
            ) {
                emitError(e, expectedErrors, "@Render method must be protected abstract");
                continue;
            }

            ExecutableElement method = (ExecutableElement) e;
            TypeMirror returnType = method.getReturnType();
            boolean ok = isReactElement(returnType);
            if (!ok) {
                emitError(e, expectedErrors, "@Render method must return React.Element");
                continue;
            }

            Set<ExecutableElement> allMethods = annotatedElementsByClass.get((TypeElement) clazz);
            if (allMethods == null) {
                allMethods = new HashSet<>();
                annotatedElementsByClass.put((TypeElement) clazz, allMethods);
            }
            allMethods.add(method);
        }

        for (Element clazz : roundEnv.getElementsAnnotatedWith(RegisterComponent.class)) {
            if (!clazz.getModifiers().contains(Modifier.ABSTRACT)) {
                emitError(clazz, expectedErrors, "@RegisterComponent can only annotate abstract class");
            }
        }

        for (Map.Entry<TypeElement, Set<ExecutableElement>> entry : annotatedElementsByClass.entrySet()) {
            RegisterComponent cReg = entry.getKey().getAnnotation(RegisterComponent.class);
            String pkg = findAndVerify(entry.getKey(), expectedErrors);
            if (pkg == null) {
                continue;
            }
            try {
                generateComponent(entry.getKey(), pkg, cReg.name(), entry.getValue(), expectedErrors);
            } catch (IOException ex) {
                emitError(entry.getKey(), expectedErrors, ex.getMessage() + " while generating " + cReg.name());
            }
        }

        TypeElement expErrType = processingEnv.getElementUtils().getTypeElement(EXP_ERR_NAME);
        if (expErrType != null) {
            for (Element e : roundEnv.getElementsAnnotatedWith(expErrType)) {
                if (!expectedErrors.contains(e)) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Expected error not emited", e);
                }
            }
        }
        return true;
    }

    private boolean isReactElement(TypeMirror returnType) {
        boolean ok = false;
        if (returnType.getKind() == TypeKind.DECLARED) {
            Element returnElement = processingEnv.getTypeUtils().asElement(returnType);
            Name returnBinName = processingEnv.getElementUtils().getBinaryName((TypeElement) returnElement);
            ok = "net.java.html.react.React$Element".equals(returnBinName.toString());
        }
        return ok;
    }

    private String findAndVerify(Element e, Set<Element> expectedErrors) {
        LinkedList<TypeElement> allButTopMostClasses = new LinkedList<>();
        PackageElement pkg;
        for (;;) {
            if (e.getKind() == ElementKind.PACKAGE) {
                pkg = ((PackageElement) e);
                break;
            }
            allButTopMostClasses.add((TypeElement) e);
            e = e.getEnclosingElement();
        }
        allButTopMostClasses.removeLast();
        for (TypeElement innerClass : allButTopMostClasses) {
            if (innerClass.getModifiers().contains(Modifier.PRIVATE)) {
                emitError(innerClass, expectedErrors, "@RegisterComponent: Make class non-private!");
                return null;
            }
            if (!innerClass.getModifiers().contains(Modifier.STATIC)) {
                emitError(innerClass, expectedErrors, "@RegisterComponent: Make class static!");
                return null;
            }
        }
        return pkg.getQualifiedName().toString();
    }

    private void printNodes(TypeElement clazz, ExecutableElement method, String indent, Node node, StringBuilder prologue1, int[] tokenCount, StringBuilder sb, Set<Element> expectedErrors) {
        if (node instanceof Text) {
            String text = node.getTextContent();
            text = text.replaceAll("\\\\", "\\\\");
            text = text.replaceAll("\\\n", "\\\\n");

            List<Token> tokens = eliminateVariables(clazz, text, prologue1, tokenCount);
            StringBuffer tmp = new StringBuffer();
            boolean comma = false;
            for(Token token : tokens) {
                switch(token.type) {
                    case TEXT:
                        if (tmp.length() > 0) {
                            tmp.append(" + ");
                        }
                        tmp.append("\"").append(token.value).append("\"");
                        break;
                    case VARIABLE:
                        if (tmp.length() > 0) {
                            tmp.append(" + ");
                        }
                        tmp.append("\"\" + ");
                        tmp.append(token.value);
                        break;
                    case CALL:
                        if (tmp.length() > 0) {
                            if (comma) {
                                sb.append(", ");
                            }
                            sb.append("React.createText(").append(tmp).append(")");
                            comma = true;
                            tmp = new StringBuffer();
                        }
                        if (comma) {
                            sb.append(", ");
                        }
                        sb.append(token.value);
                        break;
                    case MISSING:
                        emitError(method, expectedErrors, "referenced method is not found " + token.value);
                    default:
                }
                tmp = new StringBuffer(tmp.toString().trim());
            }

            if (tmp.length() > 0) {
                if (comma) {
                    sb.append(", ");
                }
                sb.append(indent).append("React.createText(").append(tmp).append(")");
            }
            return;
        }

        sb.append(indent).append("React.createElement(\"").append(node.getNodeName()).append("\", ");
        NamedNodeMap attr = node.getAttributes();
        if (attr != null && attr.getLength() > 0) {
            sb.append("\n").append(indent).append("React.props(");
            for (int i = 0; i < attr.getLength(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Attr aNode = (Attr) attr.item(i);
                Token token = eliminateVariables(clazz, aNode.getValue(), prologue1, tokenCount).get(0);
                if (token.type == Token.Type.CALL || token.type == Token.Type.VARIABLE) {
                    sb.append('"').append(aNode.getName()).append("\", ").append(token.value);
                } else {
                    sb.append('"').append(aNode.getName()).append("\", \"").append(token.value).append('"');
                }
            }
            sb.append(")");
        } else {
            sb.append("null");
        }
        sb.append(", ");
        NodeList children = node.getChildNodes();
        if (children.getLength() == 0) {
            sb.append("(React.Element[]) null");
        } else {
            final int len = children.getLength();
            for (int i = 0; i < len; i++) {
                sb.append("\n");
                printNodes(clazz, method, "  " + indent, children.item(i), prologue1, tokenCount, sb, expectedErrors);
                if (i < len - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append("\n" + indent + ")");
    }

    static class Token {
        final String value;
        final Type type;

        public Token(String value, Type type) {
            this.value = value;
            this.type = type;
        }

        enum Type {
            TEXT,
            VARIABLE,
            CALL, 
            MISSING
        }
    }
    
    private Token.Type findMethod(TypeElement clazz, String name) {
        if (!name.startsWith("this.")) {
            return Token.Type.MISSING;
        }
        for (Element e : clazz.getEnclosedElements()) {
            if (e.asType().getKind() != TypeKind.EXECUTABLE) {
                continue;
            }
            ExecutableElement m = (ExecutableElement) e;
            if (m.getSimpleName().toString().equals(name.substring(5))) {
                return m.getReturnType().toString().equals("net.java.html.react.React.Element")
                        ? Token.Type.CALL : Token.Type.VARIABLE;
            }
        }
        return Token.Type.MISSING;
    }

    private List<Token> eliminateVariables(TypeElement clazz, String text, StringBuilder prologue, int[] tokenCount) {
        List<Token> result = new ArrayList<> ();
        for (;;) {
            int at = text.indexOf("{");
            if (at == -1) {
                result.add(new Token(text, TEXT));
                break;
            } else if (at > 0) {
                result.add(new Token(text.substring(0, at), TEXT));
                text = text.substring(at);
            } else if (at == 0) {
                int end = text.indexOf('}', at);
                String inside = text.substring(1, end);
                int call = inside.indexOf('(', at);

                if (call == -1) {
                    result.add(new Token(inside, Token.Type.VARIABLE));
                } else {
                    Token.Type type = findMethod(clazz, inside.substring(0, call));
                    if (type == Token.Type.CALL) {
                        int idx = tokenCount[0]++;
                        prologue.append("    _token").append(idx).append(" = ").append(inside).append(";\n");
                        result.add(new Token("_token" + idx, type));
                    } else {
                        result.add(new Token(inside, type));
                    }
                }

                if (text.length() > end + 1) {
                    text = text.substring(end + 1);
                } else {
                    break;
                }
            }
        }
        return result;
    }

    private void emitError(Element e, Set<Element> expectedErrors, String error) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            Element anno = am.getAnnotationType().asElement();
            if (anno.getKind() != ElementKind.ANNOTATION_TYPE) {
                continue;
            }
            TypeElement type = (TypeElement) anno;
            final String typeName = type.getQualifiedName().toString();
            if (EXP_ERR_NAME.equals(typeName)) {
                AnnotationValue value = am.getElementValues().values().iterator().next();
                if (error.equals(value.getValue())) {
                    expectedErrors.add(e);
                    return;
                }
            }
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, error, e);
    }

    private void generateComponent(TypeElement key, String pkg, String name, Set<ExecutableElement> methods, Set<Element> expectedErrors) throws IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
        JavaFileObject src = processingEnv.getFiler().createSourceFile(pkg + "." + name, methods.toArray(new Element[0]));
        Writer w = src.openWriter();
        w.append("package " + pkg  + ";\n");
        w.append("import net.java.html.react.React;\n");
        w.append("final class " + name + " extends " + key + " {\n");
        w.append("  " + name + "(net.java.html.react.React.Props props) {\n");
        w.append("    super(props);\n");
        w.append("  }\n");
        for (ExecutableElement m : methods) {
            Render render = m.getAnnotation(Render.class);
            Document node;
            try {
                node = builder.parse(new ByteArrayInputStream(render.value().getBytes()));
            } catch (SAXException ex) {
                throw new IOException(ex);
            }
            w.append("  @Override\n");
            w.append("  protected final React.Element " + m.getSimpleName() + "(");

            StringBuilder prologue1 = new StringBuilder();
            StringBuilder prologue2 = new StringBuilder();
            {
                String sep = "";
                int cnt = 0;
                for (VariableElement p : m.getParameters()) {
                    TypeMirror pType = p.asType();

                    w.append(sep);
                    w.append(pType.toString());
                    int idx = cnt++;
                    w.append(" _arg" + idx);

                    prologue1.append("    ").append(pType).append(" ").append(p.getSimpleName()).append(" = ").append("_arg" + idx + ";\n");

                    ExecutableElement fn = methodOfFunctionInterface(pType);
                    if (fn != null) {
                        prologue2.append("    java.lang.Object ").append(p.getSimpleName()).append(" = net.java.html.react.React4J.wrapCallback(new net.java.html.react.React4J.Callback() {\n");
                        prologue2.append("        protected void callback(Object[] obj) {\n");
                        prologue2.append("            _arg").append(idx).append(".").append(fn.getSimpleName()).append("(");
                        String sep1 = "";
                        for (int i = 0; i < fn.getParameters().size(); i++) {
                            prologue2.append(sep1);
                            TypeMirror type = fn.getParameters().get(i).asType();
                            type = processingEnv.getTypeUtils().erasure(type);
                            prologue2.append("(").append(type).append(") obj[").append(i).append("]");
                            sep1 = ", ";
                        }
                        prologue2.append(");\n");
                        prologue2.append("        }\n");
                        prologue2.append("    });\n");
                    } else {
                        prologue2.append("    ").append(pType).append(" ").append(p.getSimpleName()).append(" = ").append("_arg" + idx + ";\n");
                    }
                    sep = ", ";
                }
            }

            StringBuilder returnValue = new StringBuilder();
            returnValue.append("    return ");

            int[] tokenCount = { 0 };
            printNodes(key, m, "    ", node.getChildNodes().item(0), prologue1, tokenCount, returnValue, expectedErrors);
            w.append(") {\n");

            for (int i = 0; i < tokenCount[0]; i++) {
                w.append("    React.Element _token" + i + ";");
            }

            w.append("    {\n");
            w.append(prologue1);
            w.append("    }\n");
            w.append("    {\n");
            w.append(prologue2);
            returnValue.append(";\n");
            w.append(returnValue.toString());
            w.append("    }\n");
            w.append("  }\n");
        }
        w.append("}\n");
        w.close();
    }

    private ExecutableElement methodOfFunctionInterface(TypeMirror type) {
        Element element = processingEnv.getTypeUtils().asElement(type);
        if (element == null || element.getKind() != ElementKind.INTERFACE) {
            return null;
        }
        ExecutableElement ee = null;
        for (Element member : element.getEnclosedElements()) {
            if (member.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            if (member.getKind() == ElementKind.METHOD) {
                if (ee != null) {
                    return null;
                }
                ee = (ExecutableElement) member;
            }
        }
        return ee;
    }
}
