// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.user

/**
 * A [FakeUserRepository] that records its writes and fails or races on cue. Scripted failures fail
 * the next call only, so a retry reaches the real fake.
 */
class ScriptedUserRepository(val delegate: FakeUserRepository = FakeUserRepository()) :
    UserRepository by delegate {
  val created = mutableListOf<User>()
  val rolesSet = mutableListOf<Role>()

  /** When set, the next [getUser] throws it. */
  var nextGetFailure: Exception? = null

  /** When set, the next [createUser] fails with it and creates nothing. */
  var nextCreateFailure: Exception? = null

  /** When set, the next [setRole] fails with it and changes nothing. */
  var nextSetRoleFailure: Exception? = null

  /** Runs just before each [createUser] reaches the fake, for example another device's writes. */
  var beforeCreate: suspend () -> Unit = {}

  override suspend fun getUser(uid: String): User? {
    nextGetFailure?.let {
      nextGetFailure = null
      throw it
    }
    return delegate.getUser(uid)
  }

  override suspend fun createUser(user: User): Result<Unit> {
    created += user
    nextCreateFailure?.let {
      nextCreateFailure = null
      return Result.failure(it)
    }
    beforeCreate()
    return delegate.createUser(user)
  }

  override suspend fun setRole(uid: String, role: Role): Result<Unit> {
    rolesSet += role
    nextSetRoleFailure?.let {
      nextSetRoleFailure = null
      return Result.failure(it)
    }
    return delegate.setRole(uid, role)
  }
}
