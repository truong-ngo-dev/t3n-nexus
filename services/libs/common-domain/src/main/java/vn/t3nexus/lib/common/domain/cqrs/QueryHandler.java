package vn.t3nexus.lib.common.domain.cqrs;

public interface QueryHandler<Q, R> {
    R handle(Q query);
}
