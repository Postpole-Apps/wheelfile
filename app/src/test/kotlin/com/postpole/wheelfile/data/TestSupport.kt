package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.attachments.AttachmentStore

/** A [RecordDeleter] wired to the DAOs of [db], for tests that build their own repositories. */
fun deleter(db: AppDatabase, store: AttachmentStore) = RecordDeleter(
    RoomTransactor(db), db.attachmentDao(), db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(),
    db.expenseDao(), db.vehicleDocumentDao(), db.reminderDao(), store,
)
