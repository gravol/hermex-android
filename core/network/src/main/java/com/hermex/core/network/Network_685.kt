package com.example.hermes.networking

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: Throwable) : NetworkResult<Nothing>()
    data class HttpError(val code: Int, val message: String) : NetworkResult<Nothing>()
}

suspend inline fun <reified T> Response<T>.handleResult(): NetworkResult<T> {
    return when {
        isSuccessful -> NetworkResult.Success(body()!!)
        isNetworkError -> NetworkResult.Error(this.exception())
        else -> {
            val errorBody = errorBody()?.string()
            NetworkResult.HttpError(code(), errorBody ?: "Unknown HTTP Error")
        }
    }
}