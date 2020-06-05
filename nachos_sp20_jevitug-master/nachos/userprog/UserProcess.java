package nachos.userprog;

import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		//initialize file descriptor table for this process
		fileDescrTable = new OpenFile[16];
		fileDescrTable[0] = UserKernel.console.openForReading();
		fileDescrTable[1] = UserKernel.console.openForWriting();
		exitStatus = null;
		exitLock = new Lock();
		parent = null;
		exitStatus = new HashMap<Integer, Integer>();
		children = new LinkedList<UserProcess>();		
		//adding a pid lock here for bullet-proofing
		UserKernel.countLock.acquire();
		pid = UserKernel.pCount;
		UserKernel.pCount++;
		UserKernel.countLock.release();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		//Lib.assertTrue(offset >= 0 && length >= 0
				//&& offset + length <= data.length);

		/*Adding code for part 2*/
		byte[] memory = Machine.processor().getMemory();
		if(vaddr < 0 || vaddr >= memory.length){
			return 0;
		}
		//copied from lib assert
		if(!(offset >= 0 && length >= 0 && offset + length <= data.length)){
			return 0;
		}
		//edge case with null data passed in
		if(data == null){
			return 0;
		}
		//get page number and offset components from provided vaddr
		int vpn = Machine.processor().pageFromAddress(vaddr);
		int vOffset = Machine.processor().offsetFromAddress(vaddr);
		//set this TranslationEntry to used
		pageTable[vpn].used = true;
		//calculate the physical address
		int addy = pageTable[vpn].ppn * pageSize + vOffset;
		//if TranslationEntry is invalid or out of range
		if(pageTable[vpn].valid == false || addy < 0 || addy >= memory.length){
			pageTable[vpn].used = false;
			return 0;
		}
		//using similar logic to handleWrite and handleRead
		//keep track of current physical and virtual addr
		int currPhys = pageTable[vpn].ppn;
		int currVir = vpn;
		//keep counter for bytes written and bytes left to write
		int bytesWritten = 0;
		int remainder = length;
		//keep track of virtual and physical offset
		int firstOff = offset;
		int secOff = vOffset;
		int currAddy = addy;	
		while(length > bytesWritten){
			//if we need more than one page
			if(pageSize < remainder + secOff){
				//call arraycopy for read, then increment bytesWritten, offset, 
				//and remaining bytes
				System.arraycopy(memory, currAddy, data, firstOff, pageSize - secOff);
				bytesWritten = bytesWritten + pageSize - secOff;
				remainder = remainder - bytesWritten;
				firstOff = firstOff + pageSize - secOff;
				//if not enough memory, break
				if(++currVir >= pageTable.length){
					break;
				}
				else{
					//since we looked ahead, go back and set currVir-1 used to false
					//and check validity of current vaddr
					pageTable[currVir - 1].used = false;
					if(pageTable[currVir].valid == false){
						break;
					}
					pageTable[currVir].used = true;
					//get ppn and calculate new phys addr
					currPhys = pageTable[currVir].ppn;
					currAddy = pageSize * currPhys;
					secOff = 0;
				}	
			}
			//if remaining bytes fit on one page
			else{
				System.arraycopy(memory, currAddy, data, firstOff, remainder);
				bytesWritten = bytesWritten + remainder;
				firstOff = firstOff + remainder;
			}
		}	
		pageTable[currVir].used = false;
		return bytesWritten;	
		/*int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);
		
		return amount;*/
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		//Lib.assertTrue(offset >= 0 && length >= 0
				//&& offset + length <= data.length);
		/*Adding code for Part 2, should be similar to readVirtualMemory*/
		//copied from Lib assert
		if(!(offset >= 0 && length >= 0 && offset + length <= data.length)){
			return 0;
		}
		if(data == null){
			return 0;
		}
		
		byte[] memory = Machine.processor().getMemory();
		
		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length){
			return 0;
		}
		//get page number and offset components from provided vaddr
		int vpn = Machine.processor().pageFromAddress(vaddr);
		int vOffset = Machine.processor().offsetFromAddress(vaddr);
		//check if read-only (cannot write)
		if(pageTable[vpn].readOnly == true){
			return 0;
		}
		pageTable[vpn].used = true;
		int addy = pageTable[vpn].ppn * pageSize + vOffset;  
		//if TranslationEntry is invalid or out of range
		if(pageTable[vpn].valid == false || addy < 0 || addy >= memory.length){
			pageTable[vpn].used = false;
			return 0;
		}
		//using similar logic to handleWrite and handleRead
		//keep track of current physical and virtual addr
		int currPhys = pageTable[vpn].ppn;
		int currVir = vpn;
		//keep counter for bytes written and bytes left to write
		int bytesWritten = 0;
		int remainder = length;
		//keep track of virtual and physical offset
		int firstOff = offset;
		int secOff = vOffset;
		int currAddy = addy;
		while(length > bytesWritten){
			//if we need more than one page
			if(pageSize < remainder + secOff){
				//arraycopy, then increment bytesWritten, offset, remaining bytes 
				System.arraycopy(data, firstOff, memory, currAddy, pageSize-secOff);
				bytesWritten = bytesWritten + pageSize - secOff;
				remainder = remainder - bytesWritten;
				firstOff = firstOff + pageSize - secOff;
				//lookahead at next value in page table
				//if address is out of bounds, break
				if(++currVir >= pageTable.length){
					break;
				}
				else{
					//need currVir-1 since we're looking ahead (++currVir)
					pageTable[currVir - 1].used = false;
					if(pageTable[currVir].valid == false || pageTable[currVir].readOnly == true){
						break;
					}
					pageTable[currVir].used = true;
					//get ppn and calculate new phys address
					currPhys = pageTable[currVir].ppn;
					currAddy = pageSize * currPhys;
					secOff = 0;
				}
			}
			//if everything fits on one page
			else{
				System.arraycopy(data, firstOff, memory, currAddy, remainder);
				bytesWritten = bytesWritten + remainder;
				firstOff = firstOff + remainder;
			}
		}
		//reset to false
		pageTable[currVir].used = false;
		return bytesWritten;

		/*	
		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;*/
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
				TranslationEntry tEnt = pageTable[vpn];
				
				//critical section?
				UserKernel.physLock.acquire();

				//check if there's enough memory	
				Integer temp = UserKernel.physPages.poll();
				if(temp == null){
					unloadSections();
					return false;	
				}
				UserKernel.physLock.release();
				//check if CoffSection is marked as read-only
				tEnt.readOnly = section.isReadOnly();
				//set physical page number to 'temp'
				tEnt.ppn = temp;
				//set valid to true
				tEnt.valid = true;
				//load this page into physical memory
				section.loadPage(i, tEnt.ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		//acquire lock to avoid race condition
		UserKernel.physLock.acquire();
		//go through and deallocate physical pages by re-adding the ppn
		//to phyPages LinkedList
		//NOTE to self: numPages is number of occupied contigious pages
		for(int i = 0; i < numPages; i++){
			UserKernel.physPages.add(pageTable[i].ppn);
		}
		UserKernel.physLock.release();
		//close all open files in this process, then close out this process
		for(int i = 0; i < 16; i++){
			if(fileDescrTable[i] != null){
				fileDescrTable[i].close();
			}
		}
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		//only the root process can successfully halt
		//if a non-root process attempts to invoke halt(), 
		//the system should not halt and the handler should immediately
		//return -1 to indicate an error
		if(pid != 0){
			Lib.debug(dbgProcess, "Cannot call halt from non-root process");
			return -1;
		}
		
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		
		//if this is a child process, add the parent(s) pid and status to
		//hashmap
		if(parent != null){
			parent.exitLock.acquire();
			parent.exitStatus.put(pid, status);
			parent.exitLock.release();
		}
		
		//go through this process' children and make the 
		//children's parents null (since this process is the parent and
		//we're exiting)	
		ListIterator<UserProcess> it = children.listIterator();
		while(it.hasNext()){
			it.next().parent = null;
		}
		//remove all children from this process' children LL
		children.clear();
	
		//Priyal told me unloadSections should go here instead of 
		//before the iterator!
		unloadSections();
		
		//decrement process counter
		UserKernel.countLock.acquire();
		UserKernel.pCount = UserKernel.pCount - 1;
		UserKernel.countLock.release();
		
		//if this process is the last one (root) to call exit,
		//call Kernel.kernel.terminate();
		//otherwise call UThread.finish()
		if(UserKernel.pCount == 0){
			Kernel.kernel.terminate();
		}
		else{
			UThread.finish();
		}
		return status;
		//Kernel.kernel.terminate();

		//return 0;
	}

	/**
	 * Handle the creat() system call
	 */
	private int handleCreate(int address){
		//if virtual address doesn't exist, return -1
		if(address < 0){
			Lib.debug(dbgProcess, "Not a valid virtual address");
			return -1;
		}
		//get String name of this process from the vaddr parameter
		//NOTE: write-up says 256 is MAX LENGTH for strings passed is 256 bytes
		String fname = readVirtualMemoryString(address, 256);
		//if fname is null, no null terminator was found and file name is invalid
		if(fname == null){
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}
		//if file name is valid, check if there's a free index in fileDescrTable
		int index = -2;
		//since fileDescrTable[0] and fileDescrTable[1] are stdin and stdout,
		//start indexing at 2
		for(int i = 2; i < 16; i++){
			//if slot is available, set index = i
			if(fileDescrTable[i] == null){
				index = i;
				break;
			}
		}
		//if there's already 16 concurrently open files for this process,
		//return -1
		if(index == -2){
			Lib.debug(dbgProcess, "All file descriptors in use");
			return -1;
		}
		//if valid file name and an available slot in descriptor table, open
		//file and create OpenFile object. This returns null if file couldn't
		//be opened
		OpenFile currFile = ThreadedKernel.fileSystem.open(fname, true);	
		if(currFile == null){
			Lib.debug(dbgProcess, "Unable to create file");
			return -1;
		}
		//otherwise add currFile to descriptor table and return the index of
		//the file
		else{
			fileDescrTable[index] = currFile;
			return index;
		}
	}

	/**
	 * Handle the open() system call
	 * NOTE: Code for handleOpen() should be similar to handleCreate()
	 *       except we don't truncate on fileSystem.open()
	 */
	
	private int handleOpen(int address){
		if(address < 0){
			Lib.debug(dbgProcess, "Not a valid virtual address");
			return -1;
		}
		String fname = readVirtualMemoryString(address, 256);
		if(fname == null){
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}
		int index = -2;
		for(int i = 2; i < 16; i++){
			if(fileDescrTable[i] == null){
				index = i;
				break;
			}
		}
		if(index == -2){
			Lib.debug(dbgProcess, "All file descriptors currently in use");
			return -1;
		}
		OpenFile currFile = ThreadedKernel.fileSystem.open(fname, false);
		if(currFile == null){
			Lib.debug(dbgProcess, "Unable to create file");
			return -1;
		}
		else{
			fileDescrTable[index] = currFile;
			return index;
		}
	}

	/**
	 * Handle the read() system call
	 */
	private int handleRead(int fd, int buf, int size){
		//if file descriptor is out of bounds, return -1;
		//bounds are 0, 2-15 (cannot be 1 since we cannot read from stdout)
		if(fd == 1 || fd < 0 || fd > 15){
			Lib.debug(dbgProcess, "File Descriptor is out of bounds!!");
			return -1;
		}
		//Get file to read
		OpenFile currFile = fileDescrTable[fd];
		//if file doesn't exist in descriptor table, return -1
		if(currFile == null){
			Lib.debug(dbgProcess, "(Read) File doesn't exist in descriptor table!!");
			return -1;
		}
		//if size is negative or extends address space, return -1
		if(size < 0 || size > Machine.processor().getMemory().length){
			Lib.debug(dbgProcess, "Size to read cannot be negative or extend beyond address space!!");
			return -1;
		}
		if(buf < 0 || buf > Machine.processor().getMemory().length){
			Lib.debug(dbgProcess, "Invalid buffer!!");
			return -1;
		}

		//initialize buffer to pass data between file and user memory
		byte[] pageBuf = new byte[pageSize];
		int remainder = size;
		//variable to keep track of total number of bytes read
		int bytesRead = 0;
		//variable to keep track of bytes recently read
		int justRead = 0;
		while(remainder > pageSize){
			//call read on OpenFile
			justRead = currFile.read(pageBuf, 0, pageSize);
			//if justRead is -1, file failed to read
			if(justRead == -1){
				Lib.debug(dbgProcess, "Failed to read file!!");
				return -1;
			}
			//if justRead = 0, we're possibly in the beginning of
			//an infinite loop and should return
			if(justRead == 0){
				return bytesRead;
			}
			
			//write to virtual memory
			int offset = writeVirtualMemory(buf, pageBuf, 0, justRead);
			//check that all bytes were written successfully
			if(justRead != offset){
				//System.out.println("Amount of bytes written doesn't match amount of bytes read!!");
				Lib.debug(dbgProcess, "Not a match!! Amount of bytes written is " +offset);
				Lib.debug(dbgProcess, "; Amount of bytes read is " + justRead);
				return -1;
			}
			//increment remainder, bytesRead, and buf
			buf = buf + offset;
			bytesRead = bytesRead + offset;
			remainder = remainder - offset;
		}
		//outside while loop, there's less than pageSize bytes left to read
		justRead = currFile.read(pageBuf, 0, remainder);
		if(justRead == -1){
			Lib.debug(dbgProcess, "Failed to read file!!");
			return -1;
		}
		//write to virtual memory
		int offset = writeVirtualMemory(buf, pageBuf, 0, justRead);
		if(justRead != offset){
			//System.out.println("Amount of bytes written doesn't match amount of bytes read!!");
			Lib.debug(dbgProcess, "Not a match!! Amount of bytes written: " + offset);
			Lib.debug(dbgProcess, "Amount of bytes read: " + justRead);
			return -1;
		}
		return (bytesRead + offset);
	}

	/**
	 * Handle the write(int fd, char *buffer, int size) system call
	 */
	private int handleWrite(int fd, int buf, int size){
		//if file descriptor is out of bounds, return -1;
		//bounds are 1-15, cannot be 0 because cannot write to stdin
		if(fd < 1 || fd > 15){
			Lib.debug(dbgProcess, "File Descriptor is out of bounds!!");
			return -1;
		}
		//Get file to write
		OpenFile currFile = fileDescrTable[fd];
		//if file doesn't exist in descriptor table, return -1
		if(currFile == null){
			Lib.debug(dbgProcess, "(Write) File doesn't exist in descriptor table!!");
			return -1;
		}
		//if size is negative or extends address space, return -1
		if(size < 0 || size > Machine.processor().getMemory().length){
			Lib.debug(dbgProcess, "Size to write can't be negative or extend beyond address space!!");
			return -1;
		}
		if(buf < 0 || buf > Machine.processor().getMemory().length){
			Lib.debug(dbgProcess, "Invalid buffer!!");
			return -1;
		}
		//initialize buffer to pass data between file and user memory
		byte[] pageBuf = new byte[pageSize];
		
		int remainder = size;
		//variable to keep track of total number of bytes written
		int bytesWritten = 0;
		//variable to keep track of bytes recently written by calls to 
		//currFile.write()
		int justWritten = 0;
		
		//Write up to pageSize bytes at a time until 'size' bytes have
		//been written	
		while(remainder > pageSize){
			//transfer data from this process's virtual mem
			//to all of pageBuf array
			justWritten = readVirtualMemory(buf, pageBuf);
			//variable to keep track of offset
			int offset = currFile.write(pageBuf, 0, justWritten); 
			//justWritten should be the same as offset if all bytes
			//were successfully written
			if(justWritten != offset){
				Lib.debug(dbgProcess, "Not all bytes were written!!");
				return -1;
			}
			//if offset is -1, failed to write to file
			if(offset == -1){
				Lib.debug(dbgProcess, "Failed to write to file!!");
				return -1;
			}
			//Adding offset = 0 case to avoid possible infinite loop
			if(offset == 0){
				//no bytes were written
				return bytesWritten;
			}
			//increment buf, bytesWritten, and remainder
			buf = buf + offset;
			bytesWritten = bytesWritten + offset;
			remainder = remainder - offset;
		}
		//if we're outside of the loop, there's less than pageSize bytes
		//left to write
		justWritten = readVirtualMemory(buf, pageBuf, 0, remainder);
		int offset = currFile.write(pageBuf, 0, justWritten);
		//as in while loop, check that bytes were successfully written
		if(justWritten != offset){
			Lib.debug(dbgProcess, "Not all bytes were written!!");
			return -1;
		}
		if(offset == -1){
			Lib.debug(dbgProcess, "Failed to write to file!!");
			return -1;
		}
		return (bytesWritten + offset);
	
	}

	/**
	 * Handle the close() system call
	 */
	private int handleClose(int fd){
		if(fd < 0 || fd > 15){
			Lib.debug(dbgProcess, "File Descriptor is out of bounds!!");
		       	return -1;	
		}
		if(fileDescrTable[fd] == null){
			Lib.debug(dbgProcess, "File doesn't exist in descriptor table!!");
			return -1;
		}
		//OpenFile currFile = fileDescrTable[fd];
		//if no errors, close file and make table index null
		//currFile.close();
		fileDescrTable[fd].close();
		fileDescrTable[fd] = null;
		return 0;
	}
	
	/**
	 * Handle the unlink(char *name) system call
	 */
	private int handleUnlink(int address){
		//if address is < 0 or > mem length, readVirtualMemory returns null	
		String fName = readVirtualMemoryString(address,256);
		//if file doesn't exist, return -1
		if(fName == null){
			Lib.debug(dbgProcess, "Unable to read file from virtual memory");
			return -1;
		}
		int index = -1;
		//check descriptor table to see if file is in there
		for(int i = 0; i < fileDescrTable.length; i++){
			OpenFile currFile = fileDescrTable[i];
			if(currFile != null && fName == currFile.getName()){
				index = i;
			}
		}
		//if file was found, call handleClose(index)
		if(index != -1){
			handleClose(index);
		}
		//remove the file from the fileSystem
		//remove() returns false if unable to remove
		if(ThreadedKernel.fileSystem.remove(fName)){
			return 0;
		}
		return -1;
	}

	/**
	 * Handle the exec(char *name, int argc, char **argv) system call
	 */
	private int handleExec(int vaddr, int argc, int argv){
		//check if vaddr is negative
		if(vaddr < 0 || vaddr > Machine.processor().getMemory().length){
			Lib.debug(dbgProcess, "Invalid address during exec()");
			return -1;
		}
		String file = readVirtualMemoryString(vaddr, 256);
		if(file == null){
			Lib.debug(dbgProcess, "Invalid file name during exec()");
			return -1;
		}
		if(argc < 0){
			Lib.debug(dbgProcess, "Error in exec(), argc cannot be negative!!");
		}
		
		//adding check to see if it's a coff file
		
		//splits 'file' into two strings based around where the '.' is
		String[] coffCheck = file.split("\\.");
		//takes the last index of the split string to check if 'coff' follows 
		//the '.'
	        String end = coffCheck[coffCheck.length - 1];
	        if(end.toLowerCase().equals("coff") == false){
			Lib.debug(dbgProcess, "Only coff files are executable");
			return -1;
		}	

		String[] arguments = new String[argc];
		//create the arguments array (args[] is normally what it's called)
		for(int i = 0; i < argc; i++){
			byte[] pointBuf = new byte[4];
			//readVirtualMemory to get the ith argument, then check to 
			//make sure it was read
			int byteRead = readVirtualMemory(argv + (i*4), pointBuf); 
			if(byteRead != 4){
				Lib.debug(dbgProcess, "Failed to read argument in exec()");
				return -1;
			}
			String temp = readVirtualMemoryString(Lib.bytesToInt(pointBuf, 0), 256);
			if(temp == null){
				Lib.debug(dbgProcess, "Failed to read argument in exec()");
				return -1;
			}
			arguments[i] = temp;
		}
		//keep track of child processes
		UserProcess child = newUserProcess();
		//execute the child with UserProcess.execute()
		//if returns true, succesful. Otherwise it failed
		if(child.execute(file, arguments)){
			children.add(child);
			child.parent = this;
			return child.pid;
		}
		else{
			Lib.debug(dbgProcess, "Unable to execute child in exec()");
			return -1;
		}	
	}

	/**
	 * Handle the join(int pid, int* status) system call
	 */
	private int handleJoin(int procId, int status){
		if(procId < 0){
			Lib.debug(dbgProcess, "Can't have negative process ID as arg in join()");
			return -1;
		}
		
		//check children LL to see if corresponding procId is in the LL
		UserProcess temp = null;
		for(int i = 0; i < children.size(); i++){
			if(procId == children.get(i).pid){
				temp = children.get(i);
				break;
			}
		}
		//if temp is still null, procId wasn't found
		if(temp == null){
			Lib.debug(dbgProcess, "Process isn't a child, or is already joined");
			return -1;
		}
		//join the 'temp' child. On return, disown child process so
		// join() cannot be used on that process again
		temp.thread.join();
		children.remove(temp);
		temp.parent = null;
		//entering critical section
		exitLock.acquire();
		Integer currStatus = exitStatus.get(temp.pid);
		exitLock.release();
		//check if currStatus is an unhandled exception
		if(currStatus == -22){
			Lib.debug(dbgProcess, "unhandled exception");
			return 0;
		}
		if(currStatus != null){
			byte[] pointBuf = new byte[4];
			Lib.bytesFromInt(pointBuf, 0, currStatus);
			if(writeVirtualMemory(status, pointBuf) == 4){
				return 1;
			}
			Lib.debug(dbgProcess, "Exited abnormally");
			return 0;	
		}
		else{
			Lib.debug(dbgProcess, "Exited abnormally");
			return 0;
		}
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			//Lib.assertNotReached("Unexpected exception");
			handleExit(-22);
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	//array to hold file table of files currently-open by this UserProcess
	// - Initialize size to 16 
	protected OpenFile[] fileDescrTable;
	//keep track of each process and its exit status
	protected HashMap<Integer, Integer> exitStatus;
	//keep track of this process' parent process (if there is one)
	protected UserProcess parent;
	//lock for controlling race conditions when updating exit status
	protected Lock exitLock;
	//each process should have a LinkedList of child processes
	protected LinkedList<UserProcess> children;
	//keep track of process id for this process
	protected int pid;
}
