/**
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
package net.java.html.react;

import java.util.HashMap;
import java.util.Map;
import net.java.html.BrwsrCtx;
import net.java.html.js.JavaScriptBody;
import net.java.html.js.JavaScriptResource;
import net.java.html.json.Models;

public class React {
    private static final Map<String,Object> FACTORIES = new HashMap<>();
    static {
        Core.initialize();
        DOM.initialize();
    }

    public static final class Element {
        private final Object js;
        private final Object[] children;
        private final Object attrs;
        private final Object type;

        Element(Object js, Object type, Object attrs, Object[] children) {
            this.js = js;
            this.type = type;
            this.attrs = attrs;
            this.children = children;
        }
    }
    
    public static final class RefObject {
        private Object current;
        
        RefObject(Object current) {
            this.current = current;
        }
    }

    public static Element createText(String text) {
        return new Element(text, null, null, null);
    }

    public static Element createElement(Object type, Object attrs, Element... children) {
        DOM.initialize();
        Object rawModel = null;
        if (attrs == null) {
            rawModel = toJS(new Object[0]);
        } else if (Models.isModel(attrs.getClass())) {
            rawModel = Models.toRaw(attrs);
        } else if (attrs instanceof Props) {
            rawModel = ((Props) attrs).js;
        }
        final Object[] rawChilden;
        if (children != null) {
            rawChilden = new Object[children.length];
            for (int i = 0; i < rawChilden.length; i++) {
                rawChilden[i] = children[i].js;
            }
        } else {
            rawChilden = null;
        }
        boolean searchForComponent = false;
        if (type instanceof String) {
            Object real = FACTORIES.get(type);
            if (real != null) {
                type = real;
            } else {
                if (((String) type).length() > 0) {
                    searchForComponent = Character.isUpperCase(((String) type).charAt(0));
                }
            }
        }
        Object js = createElement0(type, searchForComponent, rawModel, rawChilden);
        return new Element(js, type, attrs, children);
    }

    @JavaScriptBody(args = { "type", "searchViaEval", "model", "children" }, body = """
        if (searchViaEval) {
            type = (0 || eval)(type);
        }
        return React.createElement(type, model, children);
    """)
    private static native Object createElement0(Object type, boolean searchViaEval, Object model, Object[] children);

    public static Object register(String name, ComponentFactory cf) {
        Object jsClass = register0(name, cf);
        FACTORIES.put(name, jsClass);
        return jsClass;
    }
    
    public static Object createRef() {
        Object ref = createRef0();
        return ref;
    }
    
    @JavaScriptBody(args = { }, body = "return React.createRef()" )
    private static native Object createRef0();

    @JavaScriptBody(args = { "name", "factory" }, javacall = true, body = """
        let JavaReactWrapper = class __ extends React.Component {
          constructor(props) {
            super(props);
            let both = @net.java.html.react.React::factory(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)(this, props, factory);
            this.java = both[0];
            this.state = both[1];
          }

          render() {
            return @net.java.html.react.React::render(Ljava/lang/Object;)(this.java);
          }
        };

        let glob = (0 || eval)('this');
        glob[name] = JavaReactWrapper;
        return JavaReactWrapper;
    """)
    private static native Object register0(String name, ComponentFactory factory);

    private static final Map<String,Object> RENDERED = new HashMap<>();
    public static void render(String name, String id) {
        Object jsClass = FACTORIES.get(name);
        if (jsClass == null) {
            jsClass = name;
        }
        render0(null, jsClass, id);
    }

    private static final Map<String,Element> APPLIED = new HashMap<>();
    public static void render(Element reactElement, String id) {
        APPLIED.put(id, reactElement);
        render0(reactElement.js, null, id);
    }

    @JavaScriptBody(args = { "reactElem", "clazz", "id" }, body = """
        let elem = document.getElementById(id);
        if (!elem) throw 'Cannot find element ' + id;
        if (!reactElem) {
          reactElem = React.createElement(clazz);
        }
        ReactDOM.render(reactElem, elem);
    """)
    private static native void render0(Object reactElem, Object clazz, String id);

    @JavaScriptBody(args = { "comp" }, body = """
        comp.forceUpdate();
    """)
    private static native void forceUpdate(Object comp);

    static Object[] factory(Object jsThis, Object props, Object rawFactory) {
        ComponentFactory factory = (ComponentFactory) rawFactory;
        Component<?> component = factory.create(new Props(jsThis, props));
        final Object rawState;
        if (component.state == null || !Models.isModel(component.state().getClass())) {
            rawState = component.state();
        } else {
            rawState = Models.toRaw(component.state());
        }
        return new Object[] { component, rawState};
    }

    static Object render(Object rawComponent) {
        Component<?> component = (Component<?>) rawComponent;
        return component.doRender();
    }

    public interface ComponentFactory {
        Component<?> create(Props props);
    }

    public static Props props(Object... keyAndValue) {
        return new Props(null, toJS(keyAndValue));
    }

    @JavaScriptBody(args = { "keysAndValues" }, body = """
        var obj = {};
        for (let i = 0; i < keysAndValues.length; i += 2) {
          obj[keysAndValues[i]] = keysAndValues[i + 1];
        }
        return obj;
        """
    )
    private static native Object toJS(Object[] keysAndValues);

    public static final class Props {
        final Object thiz;
        final Object js;

        Props(Object thiz, Object js) {
            this.thiz = thiz;
            this.js = js;
        }

        public <T> T as(Class<T> type) {
            if (Models.isModel(type)) {
                BrwsrCtx ctx = BrwsrCtx.findDefault(Props.class);
                return Models.fromRaw(ctx, type, js);
            }
            throw new ClassCastException();
        }

        public Object get(String name) {
            return readProperty(thiz, js, name);
        }

        @JavaScriptBody(args = { "thiz", "obj", "prop" }, body = """
            debugger;
            if (thiz) obj = thiz.props;
            let val = obj[prop];
            return val;
        """)
        private static native Object readProperty(Object thiz, Object obj, String prop);

        @JavaScriptBody(args = { "thiz", "obj", "prop", "ev", "param" }, body = """
            if (thiz) obj = thiz.props;
            let fn = obj[prop];
            if (typeof fn === 'function') {
              fn(ev, param);
            } else {
              throw 'Expected function ' + fn;
            }
        """)
        private static native Object callProperty(Object thiz, Object obj, String prop, Object ev, Object param);

        public void on(String name, Object ev, Object param) {
            callProperty(thiz, js, name, ev, param);
        }
    }

    public static abstract class Component<State> {
        private final Props props;
        private boolean stateInitialized;
        private State state;

        protected Component(Props props) {
            this.props = props;
        }

        protected abstract Element render();

        protected final State state() {
            return this.state;
        }

        protected final Object getProperty(String name) {
            return this.props.get(name);
        }

        protected final void onEvent(String name) {
            onEvent(name, null, null);
        }

        protected final void onEvent(String name, Object ev, Object param) {
            this.props.on(name, ev, param);
        }

        protected final void setState(State model) {
            if (!stateInitialized) {
                this.state = model;
                this.stateInitialized = true;
                return;
            }
            this.state = model;
            forceUpdate();
        }

        protected final void forceUpdate() {
            React.forceUpdate(props.thiz);
        }

        private Element lastRender;
        private final Object doRender() {
            Element e = render();
            lastRender = e;
            return e.js;
        }
    }

    @JavaScriptResource("react.development.js")
    private static final class Core {
        @JavaScriptBody(args = {  }, body = "")
        public static native void initialize();
    }

    @JavaScriptResource("react-dom.development.js")
    private static final class DOM {
        @JavaScriptBody(args = {  }, body = "")
        public static native void initialize();
    }
}
