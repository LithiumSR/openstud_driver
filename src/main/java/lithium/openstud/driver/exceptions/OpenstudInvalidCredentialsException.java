package lithium.openstud.driver.exceptions;

public class OpenstudInvalidCredentialsException extends OpenstudBaseLoginException {
    public OpenstudInvalidCredentialsException(String message) {
        super(message);
    }

    public OpenstudInvalidCredentialsException(String message, Type type) { super(message); }

    public OpenstudInvalidCredentialsException(Exception e) { super(e); }

    @Override
    public OpenstudInvalidCredentialsException setPasswordExpiredType() {
        super.setPasswordExpiredType();
        return this;
    }

    public OpenstudInvalidCredentialsException setPasswordInvalidType() {
        super.setPasswordInvalidType();
        return this;
    }

}