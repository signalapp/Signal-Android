# Job Manager

Job Manager is part of the Signal compiled API which is hard to fully inspect its functionality. However, the main function that it provides is concurrent execution of jobs in the application.

### Diagram Summary:

- ''Job'' is the main class that every different type of job should extend and override "onRun()" method of the parent class "Job"

- ''ApplicationContext'' is the class that is responsible for storing the only JobManager instance that the rest of the application uses. 


### Sample Code Use:

- [Starting decrypt message job](https://github.com/signalapp/Signal-Android/blob/17dd681dc82935e1e588029bff44cdaa1e9aeea0/src/org/thoughtcrime/securesms/ConfirmIdentityDialog.java#L178)

 ````java
 ApplicationContext.getInstance(getContext())
                              .getJobManager()
                              .add(new PushDecryptJob(getContext(), pushId, messageRecord.getId())); 
````
