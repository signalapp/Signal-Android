package org.signal.spinner

import android.app.Application
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.helper.ConditionalHelpers
import fi.iki.elonen.NanoHTTPD
import org.signal.core.util.ExceptionUtil
import org.signal.core.util.logging.Log
import org.signal.spinner.Spinner.DatabaseConfig
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The workhorse of this lib. Handles all of our our web routing and response generation.
 *
 * In general, you add routes in [serve], and then build a response by creating a handlebars template (in the assets folder) and then passing in a data class
 * to [renderTemplate].
 */
internal class SpinnerServer(
  private val application: Application,
  deviceInfo: Map<String, () -> String>,
  private val databases: Map<String, DatabaseConfig>,
  private val plugins: Map<String, Plugin>
) : NanoHTTPD(5000) {

  companion object {
    private val TAG = Log.tag(SpinnerServer::class.java)
  }

  private val deviceInfo: Map<String, () -> String> = deviceInfo.filterKeys { !it.startsWith(Spinner.KEY_PREFIX) }
  private val environment: String = deviceInfo[Spinner.KEY_ENVIRONMENT]?.let { it() } ?: "UNKNOWN"

  private val handlebars: Handlebars = Handlebars(AssetTemplateLoader(application)).apply {
    registerHelper("eq", ConditionalHelpers.eq)
    registerHelper("neq", ConditionalHelpers.neq)
  }

  private val recentSql: MutableMap<String, Queue<QueryItem>> = mutableMapOf()
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz", Locale.US)

  override fun serve(session: IHTTPSession): Response {
    if (session.method == Method.POST) {
      // Needed to populate session.parameters
      session.parseBody(mutableMapOf())
    }

    val dbParam: String = session.queryParam("db") ?: session.parameters["db"]?.toString() ?: databases.keys.first()
    val dbConfig: DatabaseConfig = databases[dbParam] ?: return internalError(IllegalArgumentException("Invalid db param!"))

    try {
      return when {
        session.method == Method.GET && session.uri == "/css/main.css" -> newFileResponse("css/main.css", "text/css")
        session.method == Method.GET && session.uri == "/js/main.js" -> newFileResponse("js/main.js", "text/javascript")
        session.method == Method.GET && session.uri == "/" -> getIndex(dbParam, dbConfig.db())
        session.method == Method.GET && session.uri == "/browse" -> getBrowse(dbParam, dbConfig.db())
        session.method == Method.POST && session.uri == "/browse" -> postBrowse(dbParam, dbConfig, session)
        session.method == Method.GET && session.uri == "/query" -> getQuery(dbParam)
        session.method == Method.POST && session.uri == "/query" -> postQuery(dbParam, dbConfig, session)
        session.method == Method.GET && session.uri == "/recent" -> getRecent(dbParam)
        else -> {
          val plugin = plugins[session.uri]
          if (plugin != null && session.method == Method.GET) {
            getPlugin(dbParam, plugin)
          } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not found")
          }
        }
      }
    } catch (t: Throwable) {
      Log.e(TAG, t)
      return internalError(t)
    }
  }

  fun onSql(dbName: String, sql: String) {
    val commands: Queue<QueryItem> = recentSql[dbName] ?: ConcurrentLinkedQueue()

    commands += QueryItem(System.currentTimeMillis(), sql)
    if (commands.size > 500) {
      commands.remove()
    }

    recentSql[dbName] = commands
  }

  private fun getIndex(dbName: String, db: SupportSQLiteDatabase): Response {
    return renderTemplate(
      "overview",
      OverviewPageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        tables = db.getTables().use { it.toTableInfo() },
        indices = db.getIndexes().use { it.toIndexInfo() },
        triggers = db.getTriggers().use { it.toTriggerInfo() },
        foreignKeys = db.getForeignKeys(),
        queryResult = db.getTables().use { it.toQueryResult() }
      )
    )
  }

  private fun getBrowse(dbName: String, db: SupportSQLiteDatabase): Response {
    return renderTemplate(
      "browse",
      BrowsePageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        tableNames = db.getTableNames()
      )
    )
  }

  private fun postBrowse(dbName: String, dbConfig: DatabaseConfig, session: IHTTPSession): Response {
    val table: String = session.parameters["table"]?.get(0).toString()
    val pageSize: Int = session.parameters["pageSize"]?.get(0)?.toInt() ?: 1000
    var pageIndex: Int = session.parameters["pageIndex"]?.get(0)?.toInt() ?: 0
    val action: String? = session.parameters["action"]?.get(0)

    val rowCount = dbConfig.db().getTableRowCount(table)
    val pageCount = ceil(rowCount.toFloat() / pageSize.toFloat()).toInt()

    when (action) {
      "first" -> pageIndex = 0
      "next" -> pageIndex = min(pageIndex + 1, pageCount - 1)
      "previous" -> pageIndex = max(pageIndex - 1, 0)
      "last" -> pageIndex = pageCount - 1
    }

    val query = "select * from $table limit $pageSize offset ${pageSize * pageIndex}"
    val queryResult = dbConfig.db().query(query).use { it.toQueryResult(columnTransformers = dbConfig.columnTransformers) }

    return renderTemplate(
      "browse",
      BrowsePageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        tableNames = dbConfig.db().getTableNames(),
        table = table,
        queryResult = queryResult,
        pagingData = PagingData(
          rowCount = rowCount,
          pageSize = pageSize,
          pageIndex = pageIndex,
          pageCount = pageCount,
          startRow = pageSize * pageIndex,
          endRow = min(pageSize * (pageIndex + 1), rowCount)
        )
      )
    )
  }

  private fun getQuery(dbName: String): Response {
    return renderTemplate(
      "query",
      QueryPageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        query = ""
      )
    )
  }

  private fun getRecent(dbName: String): Response {
    val queries: List<RecentQuery>? = recentSql[dbName]
      ?.map { it ->
        RecentQuery(
          formattedTime = dateFormat.format(Date(it.time)),
          query = it.query
        )
      }

    return renderTemplate(
      "recent",
      RecentPageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        recentSql = queries?.reversed()
      )
    )
  }

  private fun postQuery(dbName: String, dbConfig: DatabaseConfig, session: IHTTPSession): Response {
    val action: String = session.parameters["action"]?.get(0).toString()
    val rawQuery: String = session.parameters["query"]?.get(0).toString()
    val query = if (action == "analyze") "EXPLAIN QUERY PLAN $rawQuery" else rawQuery
    val startTimeNanos = System.nanoTime()

    return renderTemplate(
      "query",
      QueryPageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        query = rawQuery,
        queryResult = dbConfig.db().query(query).use { it.toQueryResult(queryStartTimeNanos = startTimeNanos, columnTransformers = dbConfig.columnTransformers) }
      )
    )
  }

  private fun getPlugin(dbName: String, plugin: Plugin): Response {
    return renderTemplate(
      "plugin",
      PluginPageModel(
        environment = environment,
        deviceInfo = deviceInfo.resolve(),
        database = dbName,
        databases = databases.keys.toList(),
        plugins = plugins.values.toList(),
        activePlugin = plugin,
        pluginResult = plugin.get()
      )
    )
  }

  private fun internalError(throwable: Throwable): Response {
    val stackTrace = ExceptionUtil.convertThrowableToString(throwable)
      .split("\n")
      .map { it.trim() }
      .mapIndexed { index, s -> if (index == 0) s else "&nbsp;&nbsp;$s" }
      .joinToString("<br />")

    return renderTemplate("error", stackTrace)
  }

  private fun renderTemplate(assetName: String, model: Any): Response {
    val template: Template = handlebars.compile(assetName)
    val output: String = template.apply(model)
    return newFixedLengthResponse(output)
  }

  private fun newFileResponse(assetPath: String, mimeType: String): Response {
    return newChunkedResponse(
      Response.Status.OK,
      mimeType,
      application.assets.open(assetPath)
    )
  }

  private fun Cursor.toQueryResult(queryStartTimeNanos: Long = 0, columnTransformers: List<ColumnTransformer> = emptyList()): QueryResult {
    val numColumns = this.columnCount
    val columns = mutableListOf<String>()
    val transformers = mutableListOf<ColumnTransformer>()

    for (i in 0 until numColumns) {
      val columnName = getColumnName(i)
      val customTransformer: ColumnTransformer? = columnTransformers.find { it.matches(null, columnName) }

      columns += if (customTransformer != null) {
        "$columnName *"
      } else {
        columnName
      }

      transformers += customTransformer ?: DefaultColumnTransformer
    }

    var timeOfFirstRowNanos = 0L
    val rows = mutableListOf<List<String>>()
    while (moveToNext()) {
      if (timeOfFirstRowNanos == 0L) {
        timeOfFirstRowNanos = System.nanoTime()
      }

      val row = mutableListOf<String>()
      for (i in 0 until numColumns) {
        val columnName: String = getColumnName(i)
        try {
          row += transformers[i].transform(null, columnName, this)
        } catch (e: Exception) {
          row += "*Failed to Transform*\n\n${DefaultColumnTransformer.transform(null, columnName, this)}"
        }
      }

      rows += row
    }

    if (timeOfFirstRowNanos == 0L) {
      timeOfFirstRowNanos = System.nanoTime()
    }

    return QueryResult(
      columns = columns,
      rows = rows,
      timeToFirstRow = (max(timeOfFirstRowNanos - queryStartTimeNanos, 0) / 1_000_000.0f).roundForDisplay(3),
      timeToReadRows = (max(System.nanoTime() - timeOfFirstRowNanos, 0) / 1_000_000.0f).roundForDisplay(3)
    )
  }

  fun Float.roundForDisplay(decimals: Int = 2): String {
    return "%.${decimals}f".format(this)
  }

  private fun Cursor.toTableInfo(): List<TableInfo> {
    val tables = mutableListOf<TableInfo>()

    while (moveToNext()) {
      val name = getString(getColumnIndexOrThrow("name"))
      tables += TableInfo(
        name = name ?: "null",
        sql = getString(getColumnIndexOrThrow("sql"))?.formatAsSqlCreationStatement(name) ?: "null"
      )
    }

    return tables
  }

  private fun Cursor.toIndexInfo(): List<IndexInfo> {
    val indices = mutableListOf<IndexInfo>()

    while (moveToNext()) {
      indices += IndexInfo(
        name = getString(getColumnIndexOrThrow("name")) ?: "null",
        sql = getString(getColumnIndexOrThrow("sql")) ?: "null"
      )
    }

    return indices
  }

  private fun Cursor.toTriggerInfo(): List<TriggerInfo> {
    val indices = mutableListOf<TriggerInfo>()

    while (moveToNext()) {
      indices += TriggerInfo(
        name = getString(getColumnIndexOrThrow("name")) ?: "null",
        sql = getString(getColumnIndexOrThrow("sql")) ?: "null"
      )
    }

    return indices
  }

  /** Takes a SQL table creation statement and formats it using HTML */
  private fun String.formatAsSqlCreationStatement(name: String): String {
    val fields = substring(indexOf("(") + 1, this.length - 1).split(",")
    val fieldStrings = fields.map { s -> "&nbsp;&nbsp;${s.trim()},<br>" }.toMutableList()

    if (fieldStrings.isNotEmpty()) {
      fieldStrings[fieldStrings.lastIndex] = "&nbsp;&nbsp;${fields.last().trim()}<br>"
    }

    return "CREATE TABLE $name (<br/>" +
      fieldStrings.joinToString("") +
      ")"
  }

  private fun IHTTPSession.queryParam(name: String): String? {
    if (queryParameterString == null) {
      return null
    }

    val params: Map<String, String> = queryParameterString
      .split("&")
      .mapNotNull { part ->
        val parts = part.split("=")
        if (parts.size == 2) {
          parts[0] to parts[1]
        } else {
          null
        }
      }
      .toMap()

    return params[name]
  }

  private fun Map<String, () -> String>.resolve(): Map<String, String> {
    return this.mapValues { entry -> entry.value() }.toMap()
  }

  interface PrefixPageData {
    val environment: String
    val deviceInfo: Map<String, String>
    val database: String
    val databases: List<String>
    val plugins: List<Plugin>
  }

  data class OverviewPageModel(
    override val environment: String,
    override val deviceInfo: Map<String, String>,
    override val database: String,
    override val databases: List<String>,
    override val plugins: List<Plugin>,
    val tables: List<TableInfo>,
    val indices: List<IndexInfo>,
    val triggers: List<TriggerInfo>,
    val foreignKeys: List<ForeignKeyConstraint>,
    val queryResult: QueryResult? = null
  ) : PrefixPageData

  data class BrowsePageModel(
    override val environment: String,
    override val deviceInfo: Map<String, String>,
    override val database: String,
    override val databases: List<String>,
    override val plugins: List<Plugin>,
    val tableNames: List<String>,
    val table: String? = null,
    val queryResult: QueryResult? = null,
    val pagingData: PagingData? = null
  ) : PrefixPageData

  data class QueryPageModel(
    override val environment: String,
    override val deviceInfo: Map<String, String>,
    override val database: String,
    override val databases: List<String>,
    override val plugins: List<Plugin>,
    val query: String = "",
    val queryResult: QueryResult? = null
  ) : PrefixPageData

  data class RecentPageModel(
    override val environment: String,
    override val deviceInfo: Map<String, String>,
    override val database: String,
    override val databases: List<String>,
    override val plugins: List<Plugin>,
    val recentSql: List<RecentQuery>?
  ) : PrefixPageData

  data class PluginPageModel(
    override val environment: String,
    override val deviceInfo: Map<String, String>,
    override val database: String,
    override val databases: List<String>,
    override val plugins: List<Plugin>,
    val activePlugin: Plugin,
    val pluginResult: PluginResult
  ) : PrefixPageData

  data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int = rows.size,
    val timeToFirstRow: String,
    val timeToReadRows: String
  )

  data class TableInfo(
    val name: String,
    val sql: String
  )

  data class IndexInfo(
    val name: String,
    val sql: String
  )

  data class TriggerInfo(
    val name: String,
    val sql: String
  )

  data class PagingData(
    val rowCount: Int,
    val pageSize: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val firstPage: Boolean = pageIndex == 0,
    val lastPage: Boolean = pageIndex == pageCount - 1,
    val startRow: Int,
    val endRow: Int
  )

  data class QueryItem(
    val time: Long,
    val query: String
  )

  data class RecentQuery(
    val formattedTime: String,
    val query: String
  )
}
