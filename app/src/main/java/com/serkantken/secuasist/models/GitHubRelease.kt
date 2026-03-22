package com.serkantken.secuasist.models

data class GitHubRelease(
    val tag_name: String?,
    val name: String?,
    val body: String?,
    val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    val name: String?,
    val browser_download_url: String?
)
