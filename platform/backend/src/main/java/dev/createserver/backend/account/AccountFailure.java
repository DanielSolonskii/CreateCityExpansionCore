package dev.createserver.backend.account;

public class AccountFailure extends RuntimeException {
    private final int status;
    public AccountFailure(int status, String code) { super(code); this.status = status; }
    public int status() { return status; }
}
