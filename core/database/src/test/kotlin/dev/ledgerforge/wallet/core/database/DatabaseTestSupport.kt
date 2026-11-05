package dev.ledgerforge.wallet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal fun inMemoryDatabase(): ForgeDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ForgeDatabase::class.java).build()

internal fun account(
    id: String,
    balanceMinor: Long = 0,
    name: String = id,
) = AccountEntity(id, name, "SEK", balanceMinor)

internal fun transfer(
    key: String,
    source: String = "alice",
    amountMinor: Long = 100,
    createdAt: Long = 1_000,
) = OutboxEntity(
    idempotencyKey = key,
    sourceAccountId = source,
    destinationAccountId = "bob",
    amountMinor = amountMinor,
    currencyCode = "SEK",
    createdAtEpochMs = createdAt,
)

internal fun line(
    journalEntry: String,
    account: String = "alice",
    signedAmountMinor: Long = -100,
    postedAt: Long = 2_000,
) = StatementLineEntity(account, journalEntry, "TRANSFER", "transfer-$journalEntry", signedAmountMinor, "SEK", postedAt)
