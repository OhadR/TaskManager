TaskManager Exercise
===========

Exercise description 
===========

**The Application**

The Task Manager is a class that organizes the job requests by priority. It decides which job should be returned next. It arranges all the jobs by their priority — High, Normal, and Low. All jobs with high priority should be retrieved before the normal priority jobs. All jobs with normal priority should be retrieved before the low priority jobs. The order between the jobs with the same priority should be according the insertion. 

A job can be a one time task or a reoccurrence task. A reoccurrence task should first be retrieved as soon as possible (as a onetime task according its priority) and then (after the retrieval) after XX time, should be treated as if it was inserted now (according its priority) and so on... 

The job requests are inserted by multiple clients (not part of this exercise) who call the InsertJob or InsertReoccurrenceTask function. 

The job requests are retrieved by multiple clients (not part of this exercise) who call the GetNextJob function. 

The Task Manager limits the number of tasks it can hold in a single moment. 

The Task Manager should supply the following capabilities: 
* `Boolean InsertTask(int JobNumber, int Priority)` — returns `false` if there is no more space left for tasks 
* `Boolean InsertReoccurrenceTask(int JobNumber, int Priority, int Interval)` —returns `false` if there is no more space left for tasks 
	o The interval is measured in milliseconds (lsec = 1000 ms) 
* `int GetNextJob()` returns the next job in line. -1 if no jobs are available 

Task Flow 
===========
The task should be implemented according this flow: 

**1. Design document** In this document you should describe the way you are going to implement your application. Do it in a detailed way. You should describe there as much as you can (Data structures, Algorithms, components that you are going to use). This document is submitted prior to the implementation of the application. 

**2. DR**




[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/OhadR/taskmanager/trend.png)](https://bitdeli.com/free "Bitdeli Badge")

