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
import net.java.html.junit.BrowserRunner;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import cz.xelfi.demo.react4jdemo.api.Render;

@RunWith(BrowserRunner.class)
public class GenerateReactTest {
    @RegisterComponent(name = "GenerateReactRender")
    static abstract class RenderTest {
        RenderTest(React.Props p) {
        }

        @Render(
        "  <div class='empty'>\n" +
        "    <h1>Hello!</h1>\n" +
        "    <h2>Good to see you here.</h2>\n" +
        "  </div>"
        )
        protected abstract React.Element noArgs();
    }

    @Test
    public void divH1H2() {
        React.Element element = new GenerateReactRender(null).noArgs();
        assertNotNull("Element has been generated", element);
    }


}
