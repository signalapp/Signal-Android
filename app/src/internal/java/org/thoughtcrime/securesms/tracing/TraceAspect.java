package org.thoughtcrime.securesms.tracing;

import androidx.annotation.NonNull;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.thoughtcrime.securesms.logging.Log;

/**
 * Uses AspectJ to augment relevant methods to be traced with the {@link TracerImpl}.
 */
@Aspect
public class TraceAspect {

  @Pointcut("within(@org.thoughtcrime.securesms.tracing.Trace *)")
  public void withinAnnotatedClass() {}

  @Pointcut("execution(!synthetic * *(..)) && withinAnnotatedClass()")
  public void methodInsideAnnotatedType() {}

  @Pointcut("execution(!synthetic *.new(..)) && withinAnnotatedClass()")
  public void constructorInsideAnnotatedType() {}

  @Pointcut("execution(@org.thoughtcrime.securesms.tracing.Trace * *(..)) || methodInsideAnnotatedType()")
  public void annotatedMethod() {}

  @Pointcut("execution(@org.thoughtcrime.securesms.tracing.Trace *.new(..)) || constructorInsideAnnotatedType()")
  public void annotatedConstructor() {}

  @Pointcut("execution(* *(..)) && within(net.sqlcipher.database.*)")
  public void sqlcipher() {}

  @Pointcut("execution(* net.sqlcipher.database.SQLiteDatabase.rawQuery(..))             || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.query(..))                || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.insert(..))               || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.insertOrThrow(..))        || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.insertWithOnConflict(..)) || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.delete(..))               || " +
            "execution(* net.sqlcipher.database.SQLiteDatabase.update(..))")
  public void sqlcipherQuery() {}

  @Around("annotatedMethod() || annotatedConstructor() || (sqlcipher() && !sqlcipherQuery())")
  public @NonNull Object profile(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
    String methodName = joinPoint.getSignature().toShortString();

    Tracer.getInstance().start(methodName);
    Object result = joinPoint.proceed();
    Tracer.getInstance().end(methodName);
    return result;
  }

  @Around("sqlcipherQuery()")
  public @NonNull Object profileQuery(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
    String table;
    String query;

    if (joinPoint.getSignature().getName().equals("query")) {
      if (joinPoint.getArgs().length == 9) {
        table = (String) joinPoint.getArgs()[1];
        query = (String) joinPoint.getArgs()[3];
      } else if (joinPoint.getArgs().length == 7 || joinPoint.getArgs().length == 8) {
        table = (String) joinPoint.getArgs()[0];
        query = (String) joinPoint.getArgs()[2];
      } else {
        table = "N/A";
        query = "N/A";
      }
    } else if (joinPoint.getSignature().getName().equals("rawQuery")) {
      table = "";
      query = (String) joinPoint.getArgs()[0];
    } else if (joinPoint.getSignature().getName().equals("insert")) {
      table = (String) joinPoint.getArgs()[0];
      query = "";
    } else if (joinPoint.getSignature().getName().equals("insertOrThrow")) {
      table = (String) joinPoint.getArgs()[0];
      query = "";
    } else if (joinPoint.getSignature().getName().equals("insertWithOnConflict")) {
      table = (String) joinPoint.getArgs()[0];
      query = "";
    } else if (joinPoint.getSignature().getName().equals("delete")) {
      table = (String) joinPoint.getArgs()[0];
      query = (String) joinPoint.getArgs()[1];
    } else if (joinPoint.getSignature().getName().equals("update")) {
      table = (String) joinPoint.getArgs()[0];
      query = (String) joinPoint.getArgs()[2];
    } else {
      table = "N/A";
      query = "N/A";
    }

    query = query == null ? "null" : query;
    query = "[" + table + "] " + query;

    String methodName = joinPoint.getSignature().toShortString();

    Tracer.getInstance().start(methodName, "query", query);
    Object result = joinPoint.proceed();
    Tracer.getInstance().end(methodName);
    return result;
  }
}
