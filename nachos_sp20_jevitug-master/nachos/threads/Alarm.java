package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean currStatus = Machine.interrupt().disable();
		long currTime = Machine.timer().getTime();
		//Check all threads that have called Alarm.waitUntil(x)
		//Check if any thread is ready to be woken up(unblocked) - use the 
		//      wakeup time computed in waitUntil(x)
		//Change its status to ready in order to unblock it
		
		//while waitQueue isn't empty AND the current time is past the wake time
		//of the thread at the head of the queue
		while(!waitQueue.isEmpty() && waitQueue.peek().getWakeTime() <= currTime){
			//remove the head of the queue
			wThread temp = waitQueue.poll();
			//get the KThread of this waitThread
			KThread thread = temp.getThread();
			thread.ready();
		}		
		//yield the current thread and restore status
		KThread.yield();
		Machine.interrupt().restore(currStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		//if the wait parameter is 0 or negative, do nothing
		if(x <= 0){ 
			return;
		}
		
		//calculate wakeTime
		long wakeTime = Machine.timer().getTime() + x;
		
		//create a new wThread and add to wait queue
		wThread curr = new wThread(KThread.currentThread(), wakeTime);
		waitQueue.add(curr);
		
		//disable interrupts, sleep the thread, then restore	
		boolean currStatus = Machine.interrupt().disable();
		KThread.sleep();
		Machine.interrupt().restore(currStatus);
	}

	//data structure to hold waiting threads
	private PriorityQueue<wThread> waitQueue = new PriorityQueue<wThread>(10, new waitComparator());

	//container class for waiting threads that implements Comparable
	private class wThread{
		//wait thread container object that will hold a KThread and its wakeTime
		private KThread thread = null;
		private long wakeTime = -5;

		public wThread(KThread thread, long wakeTime){
			this.thread = thread;
			this.wakeTime = wakeTime;
		}
		//getter methods
		public long getWakeTime(){
			return wakeTime;
		}
		public KThread getThread(){
			return thread;
		}
	}
	
	//class to implement a comparator for the priority queue to use to sort 
	//waiting threads by waketime
	private class waitComparator implements Comparator<wThread>{
		//will use ascending order of waketimes
		public int compare(wThread w1, wThread w2){
			if(w1.getWakeTime() > w2.getWakeTime()){
				return 1;
			}
			else if(w1.getWakeTime() < w2.getWakeTime()){
				return -1;
			}
			return 0;
		}
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
		boolean currStatus = Machine.interrupt().disable();
		//temporary queue to hold threads
		boolean isThere = false;
		PriorityQueue<wThread> temp = new PriorityQueue<wThread>(10, new waitComparator());
		//loop through threads, see if reference is in there
		while(!waitQueue.isEmpty()){
			//remove the head
			wThread w = waitQueue.poll();
			//check if w is thread we're looking for
			if(w.getThread() == thread){
				isThere = true;
			}
			//put an 'else' here to remove the thread from waitQueue
			//so, calling cancel() on a thread removes it from the alarm 
			//wait queue (essentially removing the timer). Still must be
			//woken up in Condition2 
			else{
				temp.add(w);
			}
		}
		//give back the waitQueue values
	        waitQueue = temp;	
		Machine.interrupt().restore(currStatus);
		return isThere;
	}

	//Testing
	public static void alarmTest1(){
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for(int d : durations){
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}
	public static void alarmTest2(){
		int durations[] = {5, 10, 2, 3};
		long t0, t1;
		for(int d:durations){
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest2: waited for " + (t1-t0) + " ticks");
		}
	}
}
