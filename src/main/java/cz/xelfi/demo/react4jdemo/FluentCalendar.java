package cz.xelfi.demo.react4jdemo;

import java.util.function.Consumer;
import net.java.html.js.JavaScriptBody;
import net.java.html.js.JavaScriptResource;
import net.java.html.react.React;
import net.java.html.react.RegisterComponent;
import net.java.html.react.Render;

class FluentCalendar {
    @RegisterComponent(name = "FluentCalendarApp")
    static abstract class App extends React.Component<Object> {
        App(React.Props props) {
            super(props);
            setState(currentDate());
        }

        @Render("""
            <div>Selected date: {selectedDate}</div>
        """)
        protected abstract React.Element renderDate(String selectedDate);

        @Render("""
            <FluentUIReact.Calendar showGoToToday="true" onSelectDate="{onDate}" value="{selectedDate}"/>
        """)
        protected abstract React.Element renderCalendar(Consumer<Object> onDate, Object selectedDate);

        @Override
        protected React.Element render() {
            return React.createElement("div", this, 
                renderDate(dateToString(this.state())),
                renderCalendar((date) -> {
                    App.this.setState(date);
                }, this.state())
            );
        }
    }

    static void onPageLoad() {
        React.register("CalendarApp", FluentCalendarApp::new);

        FluentUIHooksLibrary.initialize();
        FluentUILibrary.initialize();

        React.render("CalendarApp", "calendar");
    }

    //
    // JavaScript/Java interop
    //

    @JavaScriptResource("fluentui-react-hooks.js")
    private static final class FluentUIHooksLibrary {
        @JavaScriptBody(args={}, body="")
        static void initialize() {
        }
    }

    @JavaScriptResource("fluentui-react.js")
    private static final class FluentUILibrary {
        static void initialize() {
            FluentUIHooksLibrary.initialize();
            initialize0();
        }

        @JavaScriptBody(args={}, body="")
        private static void initialize0() {
        }
    }

    @JavaScriptBody(args={}, body="return new Date();")
    static Object currentDate() {
        return null;
    }

    @JavaScriptBody(args={"date"}, body="return date.toLocaleDateString();")
    static String dateToString(Object date) {
        return date.toString();
    }
}
