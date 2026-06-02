package dev.anthropic.pidroid.android

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Provides read access to the device contacts via ContactsContract ContentProvider.
 */
class ContactAccessor(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Search contacts by name, email, or phone.
     *
     * @param query Search term
     * @param limit Max results to return
     * @return JSON array of contact objects with name, phone, email
     */
    fun searchContacts(query: String, limit: Int = 20): JsonArray {
        val contacts = mutableListOf<JsonObject>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val contactId = cursor.getLong(0)
                val lookupKey = cursor.getString(1) ?: continue
                val displayName = cursor.getString(2) ?: ""
                val hasPhone = cursor.getInt(3) > 0

                val phone = if (hasPhone) getPhone(contactId) else null
                val email = getEmail(contactId)

                contacts.add(buildJsonObject {
                    put("contact_id", lookupKey)
                    put("name", displayName)
                    if (phone != null) put("phone", phone)
                    if (email != null) put("email", email)
                })
                count++
            }
        }

        return JsonArray(contacts)
    }

    /**
     * Get full details for a contact by lookup key.
     */
    fun getContactDetails(contactLookupKey: String): JsonObject? {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.PHOTO_URI,
        )

        val selection = "${ContactsContract.Contacts.LOOKUP_KEY} = ?"
        val selectionArgs = arrayOf(contactLookupKey)

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId = cursor.getLong(0)
                val lookupKey = cursor.getString(1) ?: return null
                val displayName = cursor.getString(2) ?: ""
                val hasPhone = cursor.getInt(3) > 0
                val photoUri = cursor.getString(4)

                val phones = if (hasPhone) getAllPhones(contactId) else emptyList()
                val emails = getAllEmails(contactId)

                return buildJsonObject {
                    put("contact_id", lookupKey)
                    put("name", displayName)
                    if (phones.isNotEmpty()) {
                        put("phones", JsonArray(phones.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    }
                    if (emails.isNotEmpty()) {
                        put("emails", JsonArray(emails.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    }
                    if (photoUri != null) put("photo_uri", photoUri)
                }
            }
        }

        return null
    }

    private fun getPhone(contactId: Long): String? {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    private fun getAllPhones(contactId: Long): List<String> {
        val phones = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { phones.add(it) }
            }
        }
        return phones
    }

    private fun getEmail(contactId: Long): String? {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    private fun getAllEmails(contactId: Long): List<String> {
        val emails = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { emails.add(it) }
            }
        }
        return emails
    }
}
