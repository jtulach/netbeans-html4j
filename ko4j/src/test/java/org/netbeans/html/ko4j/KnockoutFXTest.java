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
package org.netbeans.html.ko4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import net.java.html.BrwsrCtx;
import net.java.html.boot.BrowserBuilder;
import net.java.html.js.JavaScriptBody;
import org.netbeans.html.boot.fx.FXGCPresenter;
import org.netbeans.html.boot.spi.Fn;
import org.netbeans.html.context.spi.Contexts;
import org.netbeans.html.json.spi.Technology;
import org.netbeans.html.json.spi.Transfer;
import org.netbeans.html.json.spi.WSTransfer;
import org.netbeans.html.json.tck.KOTest;
import org.netbeans.html.json.tck.KnockoutTCK;
import org.openide.util.lookup.ServiceProvider;
import org.testng.Assert;
import static org.testng.Assert.*;
import org.testng.annotations.Factory;

/**
 *
 * @author Jaroslav Tulach
 */
@ServiceProvider(service = KnockoutTCK.class)
public final class KnockoutFXTest extends KnockoutTCK {
    private static Class<?> browserClass;
    private static Fn.Presenter browserContext;
    
    public KnockoutFXTest() {
    }
    
    @Factory public static Object[] compatibilityTests() throws Exception {
        Class[] arr = testClasses();
        for (int i = 0; i < arr.length; i++) {
            assertEquals(
                arr[i].getClassLoader(),
                KnockoutFXTest.class.getClassLoader(),
                "All classes loaded by the same classloader"
            );
        }
        
        URI uri = DynamicHTTP.initServer();
    
        Platform.setImplicitExit(false);
        final BrowserBuilder bb = BrowserBuilder.newBrowser(new FXGCPresenter()).loadClass(KnockoutFXTest.class).
            loadPage(uri.toString()).
            invoke("initialized");
        
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                bb.showAndWait();
            }
        });
        
        ClassLoader l = getClassLoader();
        List<Object> res = new ArrayList<Object>();
        for (int i = 0; i < arr.length; i++) {
            Class<?> c = Class.forName(arr[i].getName(), true, l);
            seekKOTests(c, res);
        }
        Class<?> c = Class.forName(LessCallbacksCheck.class.getName(), true, l);
        seekKOTests(c, res);
        return res.toArray();
    }

    private static void seekKOTests(Class<?> c, List<Object> res) throws SecurityException, ClassNotFoundException {
        Class<? extends Annotation> koTest =
            c.getClassLoader().loadClass(KOTest.class.getName()).
            asSubclass(Annotation.class);
        for (Method m : c.getMethods()) {
            if (m.getAnnotation(koTest) != null) {
                if (skipUnsupported(m)) {
                    continue;
                }
                res.add(new KOFx(browserContext, m));
            }
        }
    }

    private static boolean skipUnsupported(Method m) {
        String version = System.getProperty("java.version"); // NOI18N
        if (brokenWebSockets(version) && "connectUsingWebSocket".equals(m.getName())) { // NOI18N
            return true;
        }
        return false;
    }

    private static boolean brokenWebSockets(String version) {
        return 
            "1.8.0_212".equals(version) ||
            "1.8.0_221".equals(version) ||
            "1.8.0_222".equals(version) ||
            "1.8.0_231".equals(version) ||
            "1.8.0_241".equals(version);
    }

    static synchronized ClassLoader getClassLoader() throws InterruptedException {
        while (browserClass == null) {
            KnockoutFXTest.class.wait();
        }
        return browserClass.getClassLoader();
    }

    public static synchronized void initialized(Class<?> browserCls) throws Exception {
        browserClass = browserCls;
        browserContext = Fn.activePresenter();
        KnockoutFXTest.class.notifyAll();
    }
    
    public static void initialized() throws Exception {
        Assert.assertSame(
            KnockoutFXTest.class.getClassLoader(),
            ClassLoader.getSystemClassLoader(),
            "No special classloaders"
        );
        KnockoutFXTest.initialized(KnockoutFXTest.class);
        browserContext = Fn.activePresenter();
    }
    
    @Override
    public BrwsrCtx createContext() {
        KO4J ko4j = new KO4J();
        Contexts.Builder cb = Contexts.newBuilder().
            register(Technology.class, ko4j.knockout(), 10).
            register(Transfer.class, ko4j.transfer(), 10);
        if (ko4j.websockets() != null) {
            cb.register(WSTransfer.class, ko4j.websockets(), 10);
        }
        cb.register(Executor.class, (Executor)browserContext, 10);
        cb.register(Fn.Presenter.class, browserContext, 10);
        BrwsrCtx ctx = cb.build();
        return ctx;
    }

    @Override
    public Object createJSON(Map<String, Object> values) {
        Object json = createJSON();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            setProperty(json, entry.getKey(), entry.getValue());
        }
        return json;
    }
    
    @JavaScriptBody(args = {}, body = "return new Object();")
    private static native Object createJSON();
    @JavaScriptBody(args = { "json", "key", "value" }, body = "json[key] = value;")
    private static native void setProperty(Object json, String key, Object value);

    @Override
    @JavaScriptBody(args = { "s", "args" }, body = ""
        + "var f = new Function(s); "
        + "return f.apply(null, args);"
    )
    public native Object executeScript(String script, Object[] arguments);

    @JavaScriptBody(args = {  }, body = 
          "var h;"
        + "if (!!window && !!window.location && !!window.location.href)\n"
        + "  h = window.location.href;\n"
        + "else "
        + "  h = null;"
        + "return h;\n"
    )
    private static native String findBaseURL();
    
    @Override
    public String prepareWebResource(String content, String mimeType, String[] parameters) {
        try {
            final URL baseURL = new URL(findBaseURL());
            StringBuilder sb = new StringBuilder();
            sb.append("/dynamic?mimeType=").append(mimeType);
            for (int i = 0; i < parameters.length; i++) {
                sb.append("&param" + i).append("=").append(parameters[i]);
            }
            String mangle = content.replace("\n", "%0a")
                .replace("\"", "\\\"").replace(" ", "%20");
            sb.append("&content=").append(mangle);

            URL query = new URL(baseURL, sb.toString());
            URLConnection c = query.openConnection();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            return br.readLine();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean canFailWebSocketTest() {
        try {
            Class.forName("java.util.function.Function");
            return false;
        } catch (ClassNotFoundException ex) {
            // running on JDK7, FX WebView WebSocket impl does not work
            return true;
        }
    }
}
