package com.fongmi.android.NewTV.model;

import com.fongmi.android.NewTV.Constant;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.github.catvod.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class ViewModelSearchRunner {

    private final List<Future<?>> futures;
    private final AtomicInteger epoch;

    ViewModelSearchRunner() {
        futures = new CopyOnWriteArrayList<>();
        epoch = new AtomicInteger(0);
    }

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult) {
        start(sites, taskFactory, onResult, null);
    }

    /**
     * @param onComplete 所有站点都返回后回调一次（用于判定「结果为空」）；被新的搜索取代或 stop 时不会回调。
     */
    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult, Runnable onComplete) {
        int current = nextEpoch();
        cancelFutures();
        int total = sites.size();
        if (total == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(total);
        sites.forEach(site -> execute(taskFactory.apply(site), current, result -> {
            onResult.accept(result);
            if (remaining.decrementAndGet() == 0 && onComplete != null) onComplete.run();
        }));
    }

    void stop() {
        nextEpoch();
        cancelFutures();
    }

    private int nextEpoch() {
        return epoch.incrementAndGet();
    }

    private void cancelFutures() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void execute(Callable<Result> callable, int current, Consumer<Result> onResult) {
        FluentFuture<Result> future = FluentFuture.from(Task.largeExecutor().submit(callable)).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    if (epoch.get() == current) onResult.accept(result);
                },
                // 失败/超时也回传一个空结果：保证每个站点都被计为「已返回」，完成回调才能可靠触发。
                error -> {
                    if (epoch.get() == current) onResult.accept(Result.empty());
                }
        ), MoreExecutors.directExecutor());
    }
}
