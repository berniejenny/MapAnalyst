package ika.geo.osm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A generic class that processes a list of {@link Runnable} one-by-one using
 * one or more {@link Thread}-instances.
 * 
 * @author Jan Peter Stotz
 */
public class JobDispatcher {

	protected BlockingQueue<Runnable> jobQueue = new LinkedBlockingQueue<Runnable>();

	Thread[] threads;

	public JobDispatcher(int threadCound) {
		threads = new Thread[threadCound];
		for (int i = 0; i < threadCound; i++) {
			threads[i] = new JobThread(i + 1);
		}
	}

	/**
	 * Removes all jobs from the queue that are currently not being processed.
	 */
	public void cancelOutstandingJobs() {
		jobQueue.clear();
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].interrupt();
				threads[i] = new JobThread(i + 1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void addJob(Runnable job) {
		try {
			jobQueue.put(job);
		} catch (InterruptedException e) {
		}
	}

	protected class JobThread extends Thread {

		public JobThread(int threadId) {
			super("OSMJobThread " + threadId);
			start();
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Runnable job;
				try {
					job = jobQueue.take();
				} catch (InterruptedException e1) {
					return;
				}
				try {
					job.run();
				} catch (Exception e) {
					//e.printStackTrace();
				}
			}
		}
	}

}
