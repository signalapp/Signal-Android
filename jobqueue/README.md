# JobManager

An Android library that facilitates scheduling persistent jobs which are executed when their
prerequisites have been met.  Similar to Path's android-priority-queue.

## The JobManager Way

Android apps often need to perform blocking operations.  A messaging app might need to make REST
API calls over a network, send SMS messages, download attachments, and interact with a database.

The standard Android way to do these things are with Services, AsyncTasks, or a dedicated Thread.
However, some of an app's operations might need to wait until certain dependencies are available
(such as a network connection), and some of the operations might need to be durable (complete even if the
app restarts before they have a chance to run).  The standard Android way can result in
a lot of retry logic, timers for monitoring dependencies, and one-off code for making operations
durable.

By contrast, the JobManager way allows operations to be broken up into Jobs.  A Job represents a
unit of work to be done, the prerequisites that need to be met (such as network access) before the
work can execute, and the characteristics of the job (such as durable persistence).

Applications construct a `JobManager` at initialization time:

```
public class ApplicationContext extends Application {

  private JobManager jobManager;

  @Override
  public void onCreate() {
    initializeJobManager();
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("SampleJobManager")
                                .withConsumerThreads(5)
                                .build();
  }

  ...

}
```

This constructs a new `JobManager` with 5 consumer threads dedicated to executing Jobs.  A
Job looks like this:

```
public class SampleJob extends Job {

  public SampleJob() {
    super(JobParameters.newBuilder().create());
  }

  @Override
  public onAdded() {
    // Called after the Job has been added to the queue.
  }

  @Override
  public void onRun() {
    // Here's where we execute our work.
    Log.w("SampleJob", "Hello, world!");
  }

  @Override
  public void onCanceled() {
    // This would be called if the job had failed.
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
   // Called if onRun() had thrown an exception to determine whether
   // onRun() should be called again.
   return false;
  }
}
```

A Job is scheduled simply by adding it to the JobManager:

```
  this.jobManager.add(new SampleJob());
```

## Persistence

To create durable Jobs, the JobManager needs to be given an interface responsible for serializing
and deserializing Job objects.  A `JavaJobSerializer` is included with JobManager that uses Java
Serialization, but you can specify your own serializer if you wish:

```
public class ApplicationContext extends Application {

  private JobManager jobManager;

  @Override
  public void onCreate() {
    initializeJobManager();
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("SampleJobManager")
                                .withConsumerThreads(5)
                                .withJobSerializer(new JavaJobSerializer())
                                .build();
  }

  ...

}

```

The Job simply needs to declare itself as durable when constructed:

```
public class SampleJob extends Job {

  public SampleJob() {
    super(JobParameters.newBuilder()
                       .withPersistence()
                       .create());
  }

  ...

```

Persistent jobs that are enqueued will be serialized to disk to ensure that they run even if
the App restarts first.  A Job's onAdded() method is called after the commit to disk is complete.

## Requirements

A Job might have certain requirements that need to be met before it can run.  A requirement is
represented by the `Requirement` interface.  Each `Requirement` must also have a corresponding
`RequirementProvider` that is registered with the JobManager.

A `Requirement` tells you whether it is present when queried, while a `RequirementProvider`
broadcasts to a listener when a Requirement's status might have changed.  `Requirement` is attached
to Job, while `RequirementProvider` is attached to JobManager.


One common `Requirement` a `Job` might depend on is the presence of network connectivity.
A `NetworkRequirement` is bundled with JobManager:

```
public class ApplicationContext extends Application {

  private JobManager jobManager;

  @Override
  public void onCreate() {
    initializeJobManager();
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("SampleJobManager")
                                .withConsumerThreads(5)
                                .withJobSerializer(new JavaJobSerializer())
                                .withRequirementProviders(new NetworkRequirementProvider(this))
                                .build();
  }

  ...

}
```

The Job declares itself as having a `Requirement` when constructed:

```
public class SampleJob extends Job {

  public SampleJob(Context context) {
    super(JobParameters.newBuilder()
                       .withPersistence()
                       .withRequirement(new NetworkRequirement(context))
                       .create());
  }

  ...

```

## Dependency Injection

It is possible that Jobs (and Requirements) might require dependency injection.  A simple example
is `Context`, which many Jobs might require, but can't be persisted to disk for durable Jobs.  Or
maybe Jobs require more complex DI through libraries such as Dagger.

JobManager has an extremely primitive DI mechanism strictly for injecting `Context` objects into
Jobs and Requirements after they're deserialized, and includes support for plugging in more complex
DI systems such as Dagger.

The JobManager `Context` injection works by having your `Job` and/or `Requirement` implement the
`ContextDependent` interface.  `Job`s and `Requirement`s implementing that interface will get a
`setContext(Context context)` call immediately after the persistent `Job` or `Requirement` is
deserialized.

To plugin a more complex DI mechanism, simply pass an instance of the `DependencyInjector` interface
 to the `JobManager`:

```
public class ApplicationContext extends Application implements DependencyInjector {

  private JobManager jobManager;

  @Override
  public void onCreate() {
    initializeJobManager();
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("SampleJobManager")
                                .withConsumerThreads(5)
                                .withJobSerializer(new JavaJobSerializer())
                                .withRequirementProviders(new NetworkRequirementProvider(this))
                                .withDependencyInjector(this)
                                .build();
  }

  @Override
  public void injectDependencies(Object object) {
    // And here we do our DI magic.
  }

  ...

}
```

`injectDependencies(Object object)` will be called for a `Job` before the job's `onAdded()` method
is called, or after a persistent job is deserialized.