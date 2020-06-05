Jared Vitug, A92083122

Part 1: This part was pretty straightforward, through testing with the provided syscall tests I figured out
        how to bullet-proof each method with appropriate if statements.  For read and write, I used a pageSize
        buffer to pass data between file and user memory using a while loop that checked if there was more than
        a buffered amount of data left to write.  For write, I'd then check if the correct amount of data was written
        and made sure it was written in the correct place.  Read() was pretty much the same as Write(). I tested with the
        provided tests and modified write1 and write4 with bad inputs to make sure the machine was halting or throwing an
        error as required.
   
Part 2: The most difficult part for me about this portion of the project was figuring out an algorithm that worked for
        converting from virtual to physical memory and vice versa.  I ended up using a similar algorithm to handleRead()
        and handleWrite(), iterating through the pageTable and keeping track of the current virtual/physical offsets, 
        addresses, and bytes written and remaining bytes to write.  I did this for both readVirtualMemory and writeVirtualMemory.
        For loadSections() I implemented several locks in UserKernel to be used for critical sections, including in loadSections()
        when I would get the next available page from a LinkedList of physical pages (in UserKernel). For unloadSections() I went 
        through and 'put back' the pages from pageTable into the physical pages Linked List in UserKernel, then looped through the
        array of OpenFiles and closed each for that process.  After I felt like my algorithms were working for Part 2's methods I
        went back and added the correct TranslationEntry variable checks and sets (like checking/setting readOnly). I tested with 
        the same tests from Part 1.
        
Part 3: I honestly felt like Part 2 was harder than Part 3, but this part required a lot of looking up syntax of stuff (conversion
        of bytes to int and header files for syntax).  I used a HashMap that mapped children's process Id's with their exit status and a 
        Linked List that held a process' children.  For halt() I just checked to make sure the root process was calling halt and then
        would call Machine.halt(), otherwise return -1.  For exit() I checked if this process was a child (meaning it has a parent) and
        then would add this process and the status parameter to the parent's HashMap of children and exit statuses. Then go through this
        process' children and make their parent null (since this process is closing) and call finish() if this wasn't the root or 
        terminate() if it was the root.  For join() I'd check to see if the parameter pid matched any of this process' children.
        Then call join() on the child's thread and upon return from this call, remove the child from the original process' children.
        Then check if the status was -22 (added in exceptionHandler for unhandled exceptions) or not null and returned the appropriate
        value.  For exec() I'd loop through the arguments and convert them to strings, store them in a string array,
        create a new UserProcess that then called execute() using the provided vaddr and the arguments array. 
        If execute worked (returns true) I added this new UserProcess to the children Linked List of the current Process and returned
        the child's pid.
