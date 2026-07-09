val groupedSessions = sessions.groupBy { it.date }
    groupedSessions.entries.sortedBy { it.key }