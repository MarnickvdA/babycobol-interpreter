package nl.utwente.interpreter.exception;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

public class InterpreterException extends RuntimeException {
    public InterpreterException(ParserRuleContext ctx, String s ) {

        super(buildMessage(ctx, s));
    }

    public InterpreterException(Throwable throwable) {
        super(throwable.getMessage());
    }

    private static String buildMessage( ParserRuleContext ctx, String msg ) {
        if (ctx != null) {
            Token firstToken = ctx.getStart();
            int line = firstToken.getLine();
            return String.format("line: %s, message: %s", line, msg);
        } else {
            return "No ctx provided! message: " + msg;
        }
    }
}
