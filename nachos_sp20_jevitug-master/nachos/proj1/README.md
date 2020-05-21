Group Members: Jared Vitug (A92083122)

waitUntil(long x): I checked if the method parameter was less than or equal to zero, in which case it would return. I then calculated
                   the wakeup time by getting the sum of the method paramter and the current time. I then added the current thread to 
                   a priority queue of wThreads, which is a container class I created to hold the threads and their wake times so that
                   (using a wThread comparator I wrote) the priority queue could schedule threads accordingly based on nearest scheduled
                   wake time. I then disabled interrupts, entered a critical section where the thread is slept, and re-enabled interrupts.
              
timerInterrupt(): I disabled interrupts and got the current time, then checked if the global PriorityQueue<wThreads> was empty or not and
                  if the next thread on it was at or past its wakeup time. If it was, then I'd wake it up by readying it.
      
join(): I first checked if the calling thread was the same as the current thread or if the calling thread had finished already,
        in which case I'd return.  I then would set a global isJoinable flag to false (since join can't be called on a thread 
        multiple times), added this thread to the ThreadQueue, and slept it. In the 'finish()'  function I added code to unblock the
        original thread.
  
sleepFor(long timeout): I again check to make sure 'timeout' isn't zero or negative (in this case return).  Then I disable interrupts,
                        release the lock, add this thread to the Condition2 waiting threads queue, and call waitUntil(timeout). Upon
                        returning from this call, the thread re-acquires the lock and interrupts are restored.
                     
cancel(KThread thread): I created a temporary queue to hold threads and was essentially making a copy of the global queue EXCEPT if the
                        thread being called to cancel was found in Alarm's queue, I'd remove it from Alarm's queue. I also added code in
                        wake() and wakeAll() to check if a thread in Condition2's waitThread queue had already been awakened from 
                        Alarm's queue.
                        
exchange(int tag, int value):  for the Rendezvous object, I gave it only a global Hashtable that held <tags, rendSubObj>, where 
                               rendSubObj was a container object I used to store flags for whether or not a thread was the second of
                               its respective tag to call exchange.  The object also contained a LinkedList to hold older thread values
                               so that correct values were returned.  The logic inside the exchange method is to check if the 
                               Rendezvous Hashtable already contains the 'tag' - if it doesn't, create a new rendSubObj; if it exists,
                               get that tag's rendSubObj.  Then check if isAcquired (flag used to determine if we should be blocking
                               or waking) is set.  If isAcquired is false, sleep the current thread; if it's true, get the value from
                               the LinkedList to return for this second thread and wake the first thread.
                               
                               When testing exchange, I did a lot of print statements to trace where in my code I was getting stuck 
                               (before I used a LinkedList for older values).  I then created two more KThread objects with different
                               tags and values to see if exchange worked properly.  This is what I did when testing each of the methods
                               - I'd use the given test and simply add edge cases to it to test my methods.
