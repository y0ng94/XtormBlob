package ty.module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author	: y0ng94
 * @version	: 1.0
 */
public class ExecutorHelper {
	private ExecutorService executorService;	// Global variables executor service
	private int threadCount = 0;				// Thread count
	
	// Initialize executor service
	public void init(int threadCount) {
		this.threadCount = threadCount;
		if (threadCount <= 1)
			this.executorService = Executors.newSingleThreadExecutor();
		else
			this.executorService = Executors.newFixedThreadPool(threadCount);
	}

	// Initialize executor service 
	public void init(int threadCount, ExecutorService executorService) {
		this.threadCount = threadCount;
		this.executorService = executorService;
	}
	
	// Exit the executor service, and force it to exit if it is alive after the waiting time.
	public void shutdown(int minutes) throws InterruptedException {
		try {
			executorService.shutdown();
			if (!executorService.awaitTermination(minutes, TimeUnit.MINUTES))
				exterminate();
		} catch (InterruptedException e) {
			throw new InterruptedException();
		}
	}
	
	// Force the executioner service to shut down
	public void exterminate() {
		executorService.shutdownNow();
	}
	
	// Execute the received runnable as many threads as possible.
	public void execute(Runnable runnable) {
		IntStream.range(0, threadCount).forEach(i -> {
			executorService.execute(runnable);
		});
	}

	// Execute the received callable and return the result value of future.
	public List<Integer> execute(List<Callable<Integer>> callable) throws InterruptedException, ExecutionException {
		List<Future<Integer>> futures = executorService.invokeAll(callable);
		List<Integer> integers = new ArrayList<>();

		for (Future<Integer> future : futures)
			integers.add(future.get());

		return integers;
	}

	// Execute the received callable and return the result value of future.
	public List<String> execute(Callable<String>[] callables) throws InterruptedException, ExecutionException {
		List<Future<String>> futures = new ArrayList<>();
		List<String> strings = new ArrayList<>();

		for (Callable<String> callable : callables)
			futures.add(executorService.submit(callable));

		for (Future<String> future : futures)
			strings.add(future.get());
		
		return strings;
	}
}
