package nachos.threads;

import nachos.machine.*;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
	//private KThread thread;
	//private HashMap<Integer, Condition2> map;
	//Hashtable<Integer, Condition2> hm;
	Hashtable<Integer, rendSubObj> tagTable;	
	/*
	private int rVal;
	private Condition2 cv2;
	private Lock conditionLock;
	private boolean isAcquired;
	private Condition cv;
	private LinkedList<Integer> oddThreads;
	*/
    public Rendezvous () {
	tagTable = new Hashtable<Integer, rendSubObj>();	
	/*rVal = -5;
	conditionLock = new Lock();
        cv2 = new Condition2(conditionLock);
	//cv = new Condition(conditionLock);
	isAcquired = false;
	oddThreads = new LinkedList<Integer>();
        */		
	//map = new HashMap<Integer, Condition2>();	
    	//hm = new Hashtable<Integer, Condition2>();
    }
    
    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
	    //boolean isAcquired;
	    //int rVal = -1000;
	    //Condition2 cv2;
	    //Lock conditionLock;
	    //LinkedList<Integer> oList;
	    //if the tag is already contained in the hashtable, get
	    //the associated rendezvous sub object
	    rendSubObj r;
	    System.out.println("Hashtable size is: " + tagTable.size());
	    if(tagTable.containsKey(tag)){
		System.out.println("should go here second");
		r = tagTable.get(tag);	
	    }
	    //if the tag isn't already in the hashtable, make a new
	    //rendezvous sub object associated with this tag and
	    //add it to the hashtable
	    else{
	        System.out.println("should go here first");
		r = new rendSubObj(value);
		tagTable.put(tag, r);
	    } 
	    
	    //if this is first thread calling exchange
	    if(!(r.isAcquired)){
	        boolean currStatus = Machine.interrupt().disable();
		r.isAcquired = true;
		//thread = KThread.currentThread();
		r.rVal = value;
		r.conditionLock.acquire();
		Machine.interrupt().restore(currStatus);
		System.out.println("sleeping thread " + KThread.currentThread());
		r.cv2.sleep();
		//cv.sleep();
		r.conditionLock.release();
		int temp = r.oddThreads.removeFirst();
		//return rVal;
		return temp;
	    }
	    //if this is second thread calling exchange
	    else{
	        boolean currStatus = Machine.interrupt().disable();
		r.isAcquired = false;
		int temp = r.rVal;
		//rVal = value;
		r.oddThreads.add(value);
		r.conditionLock.acquire();
		Machine.interrupt().restore(currStatus);	
		r.cv2.wake();
		//cv.wake();
		r.conditionLock.release();
		return temp;
	    } 
    }
   

    //container object, to be used in rendezvous object's hash table
    //
    //Each unique tag should have its own rendObj 
    private class rendSubObj{
	public int rVal;
	public Condition2 cv2;
	public Lock conditionLock;
	public boolean isAcquired;
	public LinkedList<Integer> oddThreads;
	
	public rendSubObj(int value){
		rVal = value;
		conditionLock = new Lock();
		cv2 = new Condition2(conditionLock);
		isAcquired = false;
		oddThreads = new LinkedList<Integer>();
	}
    } 
    
    public static void rendezTest1(){
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread(new Runnable() {
		public void run() {
			int tag = 0;
			int send = -1;
			System.out.println("Thread " + KThread.currentThread().getName() +
					" exchanging " + send);
			int recv = r.exchange(tag, send);
			Lib.assertTrue(recv == -2, "Was expecting "+ -2 +" but recieved "+recv);
			System.out.println("Thread "+KThread.currentThread().getName()+
					" recieved " + recv);
		}
	});
	t1.setName("t1");
	KThread t2 = new KThread(new Runnable() {
		public void run() {
			int tag = 1;
			int send = 1;
			System.out.println("Thread " + KThread.currentThread().getName() +	
					" exchanging " + send);	
			int recv = r.exchange(tag, send);
			Lib.assertTrue(recv == 2, "Was expecting "+ 2 +" but recieved "+recv);
			System.out.println("Thread "+KThread.currentThread().getName()+
					" received " + recv);	
		}
	});
	t2.setName("t2");

	//adding third thread with same tag
	KThread t3 = new KThread(new Runnable() {
		public void run() {
			int tag = 1;
			int send = 2;
			System.out.println("Thread " + KThread.currentThread().getName() +
					" exchanging " + send);
			int recv = r.exchange(tag, send);
			Lib.assertTrue(recv == 1, "Was expecting "+ 1 + " but recieved "+recv);
			System.out.println("Thread "+KThread.currentThread().getName()+
					" received " + recv);
		}
	});
	t3.setName("t3");
	
	//adding fourth thread with same tag
	KThread t4 = new KThread(new Runnable() {
		public void run() {
			int tag = 0;
			int send = -2;
			System.out.println("Thread " + KThread.currentThread().getName() +
					" exchanging " + send);
			int recv = r.exchange(tag, send);
			Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but recieved "+recv);
			System.out.println("Thread "+KThread.currentThread().getName()+
					" received " + recv);
		}
	});
	t4.setName("t4");


	t1.fork(); t2.fork(); /*adding t3 and t4 */t3.fork(); t4.fork();
	t1.join(); t2.join(); /*adding t3 and t4 */t3.join(); t4.join();
    }

    public static void selfTest(){
	rendezTest1();
    }
}
