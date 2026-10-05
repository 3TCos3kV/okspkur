package com.acute.domain

sealed class DomainError(message: String) : RuntimeException(message)
class NotFound(message: String) : DomainError(message)
class Conflict(message: String) : DomainError(message)
class Invalid(message: String) : DomainError(message)
