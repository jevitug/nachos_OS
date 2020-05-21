package nachos.threads;

import nachos.machine.*;
import java.util.*;
/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		//added ThreadQueue to constructor to hold waiting threads
		this.waitingThreads = ThreadedKernel.scheduler.newThreadQueue(true);
		
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		//assert if thread doesn't have the lock associated with CV
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		//disable interrupts
		boolean currStatus = Machine.interrupt().disable();
	
		//if thread holds lock, add it to the ThreadQueue
		waitingThreads.waitForAccess(KThread.currentThread());
		
		//release the lock from the thread, then sleep
		conditionLock.release();
		//System.out.println("Sleeping thread: " + KThread.currentThread().getName());
		KThread.sleep();
		
		//when thread wakes, it should reacquire lock
		//System.out.println("current lockHolder is: "+ conditionLock.getLockHolder().getName());
		//System.out.println("Thread " + KThread.currentThread().getName() +" just woke up!");
		conditionLock.acquire();
		//restore interrupt state
		//System.out.println("did we get to line after acquire??");
		Machine.interrupt().restore(currStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		//need to implement if cancel returns true
		//never on alarm queue, on alarm queue but not woken up by timer (cancel), 
		//timer expires (in Alarm class) (check condition variables queue doesn't 
		//have that thread remove)
		//check if status is not 3	
		
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean currStatus = Machine.interrupt().disable();
		KThread temp = waitingThreads.nextThread();
		//if waiting threads isn't empty
		if(temp != null){
			//if thread is already awake,, remove it from
			//Condition2 wait queue
			if(temp.getStatus() != 3){

			}
			//if status of thread is equal to 3
			else{
				ThreadedKernel.alarm.cancel(temp);
				//System.out.println("readying thread: " + temp.getName());
				temp.ready();
			}
		}
		Machine.interrupt().restore(currStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		//check if current thread holds the associated lock
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		//disable interrupts
		boolean currStatus = Machine.interrupt().disable();
		//while there's still threads waiting in waitingThreads, wake each thread
		KThread temp = waitingThreads.nextThread();
		while(temp != null){
			//if thread is already awake, remove it from
			//Condtion2 wait queue
			if(temp.getStatus() != 3){

			}
			else{
				temp.ready();
				temp = waitingThreads.nextThread();
			}
		}
		//restore interrupts
		Machine.interrupt().restore(currStatus);
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
        public void sleepFor(long timeout) {
		if(timeout <= 0){
			return;
		}
		
		//check if current thread holds associated lock
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		//have this thread waitUntil(timeout)
		boolean currStatus = Machine.interrupt().disable();	
		conditionLock.release();
		waitingThreads.waitForAccess(KThread.currentThread());
		ThreadedKernel.alarm.waitUntil(timeout);
		conditionLock.acquire();
		Machine.interrupt().restore(currStatus);
	}

	//data structure to hold waiting threads 
	private ThreadQueue waitingThreads = null;
	//PriorityQueue<KThread> waitingThreads = new PriorityQueue<KThread>();	

        private Lock conditionLock;
	
	//test for part 3
	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;
		//private static Condition cv;

		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for(int i = 0; i < 10; i++){
					System.out.println(KThread.currentThread().getName());
					cv.wake();  //signal
					cv.sleep(); //wait
				}
				lock.release();
			}
		}
		public InterlockTest() {
			lock = new Lock();
			cv = new Condition2(lock);
			//cv = new Condition(lock);
			
			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();

			// We need to wait for ping to finish, and the proper way
			// to do so is to join on ping.  (Note that, when ping is
			// done, pong is sleeping on the condition variable; if we
			// were also to join on pong, we would block forever.)
			// For this to work, join must be implemented.  If you
			// have not implemented join yet, then comment out the 
			// call to join and instead uncomment the loop with 
			// yields; the loop has the same effect, but is a kludgy
			// way to do it.
			ping.join();
			//for(int i = 0; i < 50; i++) {KThread.currentThread().yield();}
		}
	}
	
	public static void cvTest5(){
		final Lock lock = new Lock();
		final Condition empty = new Condition(lock);
		//final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run(){
				lock.acquire();
				while(list.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list.size()==5, "List should have 5 values.");
				while(!list.isEmpty()){
					//context switch for the fun of it
					System.out.println("inside while loop for cons");
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});
		KThread producer = new KThread( new Runnable() {
			public void run() {
				lock.acquire();
				for(int i = 0; i < 5; i++){
					list.add(i);
					System.out.println("Added " + i);
					//context switch for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});
		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them.  For this
		// to work, join must be implemented.  If you have not
		// implemented join yet, then comment out the calls to join 
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		//for(int i = 0; i < 50; i++) {KThread.currentThread().yield();}
	}

	private static void sleepForTest1(){
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + 
				" woke up, slept for " + (t1-t0) + " ticks");
		lock.release();
	}

	public static void selfTest() {
		//new InterlockTest();
		//cvTest5();
		sleepForTest1();
	}
}
