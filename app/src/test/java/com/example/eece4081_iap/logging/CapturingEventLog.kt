package com.example.eece4081_iap.logging

/** Test double: keeps every record so tests can check what was logged. */
class CapturingEventLog : EventLog {
    val records = mutableListOf<LogRecord>()

    /** Set to true to act like verbose mode, where detail records are wanted. */
    var detail = false

    override fun record(record: LogRecord) {
        records += record
    }

    override fun wantsDetail(): Boolean = detail

    fun events(): List<String> = records.map { it.event }

    fun only(event: String): LogRecord = records.single { it.event == event }
}
