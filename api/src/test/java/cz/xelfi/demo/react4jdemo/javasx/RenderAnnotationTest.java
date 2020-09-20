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
package cz.xelfi.demo.react4jdemo.javasx;

import cz.xelfi.demo.react4jdemo.api.React;
import cz.xelfi.demo.react4jdemo.api.RegisterComponent;
import cz.xelfi.demo.react4jdemo.api.Render;

public class RenderAnnotationTest {
    static abstract class RenderWithRegister {
        @ExpectedError("@Render in a class without @RegisterComponent")
        @Render("<h1>no register</h1>")
        protected abstract React.Element renderSomething();
    }
    @RegisterComponent(name = "RenderClassNotAbstract")
    @ExpectedError("@RegisterComponent can only annotate abstract class")
    static class RenderClassNotAbstract {
    }
    @RegisterComponent(name = "RenderNotAbstract")
    static abstract class RenderNotAbstract {
        @ExpectedError("@Render method must be protected abstract")
        @Render("<h1>no register</h1>")
        protected React.Element renderSomething() {
            return null;
        }
    }
    @RegisterComponent(name = "RenderNotProtected")
    static abstract class RenderNotProtected {
        @ExpectedError("@Render method must be protected abstract")
        @Render("<h1>no register</h1>")
        abstract React.Element renderSomething();
    }
    @RegisterComponent(name = "RenderWrongReturnType")
    static abstract class RenderWrongReturnType {
        @ExpectedError("@Render method must return React.Element")
        @Render("<h1>no register</h1>")
        protected abstract void renderSomething();
    }
    @ExpectedError("@RegisterComponent: Make class static!")
    @RegisterComponent(name = "RenderNotStatic")
    abstract class RenderNotStatic {
        @Render("<h1>no register</h1>")
        protected abstract React.Element renderSomething();
    }
    @ExpectedError("@RegisterComponent: Make class non-private!")
    @RegisterComponent(name = "RenderNotPrivate")
    private static abstract class RenderNotPrivate {
        @Render("<h1>no register</h1>")
        protected abstract React.Element renderSomething();
    }
}

