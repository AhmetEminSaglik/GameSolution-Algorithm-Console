package errormessage;

public class ErrorMessage {

    public static void appearFatalError(Class className, String message) {
        new FatalErrorMessage().showMessage("Class where is the error occured : << " + className.getCanonicalName() + " >>  Error Message : " + message);

    }

    public static void appearClassicError(Class className, String message) {
        new ClassicErrorMessage().showMessage("Class where is the error occured : << " + className.getCanonicalName() + " >>  Error Message : " + message);
    }

    public static void appearWarnings(Class className, String message) {
        new WarningMessage().showMessage("Class where is the WARNING occured : << " + className.getCanonicalName() + " >>  Error Message : " + message);
    }
}
