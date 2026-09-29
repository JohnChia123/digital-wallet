package com.example.wallet.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * MDC is thread-local, so an @Async method (which runs on a pool thread, not the request thread)
 * would otherwise lose the correlation ID CorrelationIdFilter put in place. Spring Boot's
 * auto-configured task executor (used for @Async, since no explicit Executor bean is defined)
 * picks up any TaskDecorator bean automatically, so this alone is enough to make it propagate.
 */
@Configuration // tells Spring this class contributes @Bean definitions to the application context
public class AsyncConfig {

    // The bean's type is TaskDecorator -- Spring Boot's TaskExecutorConfigurations (inside
    // spring-boot-autoconfigure) looks up a bean of exactly this type via ObjectProvider<TaskDecorator>
    // when it builds the executor @Async uses, and wires it in automatically. Nothing in this
    // codebase ever calls this method or .decorate(...) directly.
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        // TaskDecorator is a functional interface: Runnable decorate(Runnable runnable).
        // This lambda IS the TaskDecorator instance -- its parameter `runnable` is the real
        // @Async task Spring will eventually hand us; its body is what `decorate(...)` returns.
        return runnable -> {
            // Runs on the SUBMITTING thread (the original HTTP request thread), at the moment
            // @Async hands the task off -- i.e. before the pool thread ever gets involved.
            // Snapshots whatever CorrelationIdFilter (or anything else) put into MDC right now.
            Map<String, String> context = MDC.getCopyOfContextMap();

            // This inner lambda is the actual Runnable being returned from decorate(...) --
            // it's what the executor stores and runs LATER, on a pool thread. Nothing inside
            // here executes yet; it only runs once the executor picks this task up.
            return () -> {
                // Runs on the POOL thread, right before the real task executes. Pool threads are
                // reused across many unrelated tasks, so capture whatever MDC content (if any)
                // this thread already has, so it can be put back afterward instead of erased.
                Map<String, String> previous = MDC.getCopyOfContextMap();

                if (context != null) {
                    // Installs the snapshot taken on the request thread onto this pool thread's
                    // (previously empty, or previously-someone-else's) MDC.
                    MDC.setContextMap(context);
                }
                try {
                    // Actually runs the real @Async method body (e.g. PaymentProcessor.
                    // processDepositAsync) -- by now MDC.get("correlationId") on this thread
                    // returns the same value it did back on the original request thread.
                    runnable.run();
                } finally {
                    // Cleanup runs whether the task succeeded or threw. Restore what was on this
                    // thread before we overwrote it, rather than unconditionally wiping MDC --
                    // if some other code had legitimately set MDC content on this pool thread
                    // before our task ran, that content is put back instead of lost. In practice,
                    // for this app, `previous` is always null here (every task that touches this
                    // thread cleans up after itself the same way), so this ends up behaving the
                    // same as always clearing -- it's just the more defensive version of it.
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
