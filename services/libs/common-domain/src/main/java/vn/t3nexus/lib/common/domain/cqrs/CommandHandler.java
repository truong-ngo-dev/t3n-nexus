package vn.t3nexus.lib.common.domain.cqrs;

public interface CommandHandler<C, R> {
    R handle(C command);
}
