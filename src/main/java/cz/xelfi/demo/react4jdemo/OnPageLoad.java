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
package cz.xelfi.demo.react4jdemo;

public final class OnPageLoad {
    private OnPageLoad() {
    }

    public static void main(String... args) {
        String page;
        try {
            page = args[0];
        } catch (Exception ex) {
            page = "";
        }

        String code;
        switch (page) {
            case "like+model":
                LikeButtonNoJavaFX.onPageLoad();
                code = "LikeButtonNoJavaFX.java";
                break;
            case "ttt1":
                TicTacToe1.onPageLoad();
                code = "TicTacToe1.java";
                break;
            case "ttt2":
                TicTacToe2.onPageLoad();
                code = "TicTacToe2.java";
                break;
            case "ttt3":
                TicTacToe3.onPageLoad();
                code = "TicTacToe3.java";
                break;
            case "calendar":
                FluentCalendar.onPageLoad();
                code = "FluentCalendar.java";
                break;
            default:
                LikeButton.onPageLoad();
                code = "LikeButton.java";
        }

        CodeComponent.loadCode(code);
    }
}
