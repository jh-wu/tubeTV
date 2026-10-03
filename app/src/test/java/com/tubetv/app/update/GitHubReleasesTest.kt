package com.tubetv.app.update

import com.tubetv.app.data.update.GitHubReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleasesTest {

    // Trimmed from https://api.github.com/repos/jh-wu/tubetv/releases/latest
    private val latest = """
        {"url":"https://api.github.com/repos/jh-wu/tubetv/releases/1","tag_name":"build-12","name":"tubeTV build 12",
         "body":"Add online updates\n\nChecks GitHub for a newer build.\n\nCo-Authored-By: someone\n\nBuilt from abc123. Install: https://github.com/jh-wu/tubetv/releases/latest/download/tubetv.apk",
         "assets":[{"name":"tubetv.apk","size":13195261,
           "browser_download_url":"https://github.com/jh-wu/tubetv/releases/download/build-12/tubetv.apk"}]}
    """.trimIndent()

    @Test fun parsesLatestRelease() {
        val r = GitHubReleases.parse(latest)!!
        assertEquals(12, r.build)
        assertEquals("tubeTV build 12", r.title)
        assertEquals("https://github.com/jh-wu/tubetv/releases/download/build-12/tubetv.apk", r.apkUrl)
        assertEquals(13195261L, r.sizeBytes)
        assertEquals("Add online updates\n\nChecks GitHub for a newer build.", r.notes)
    }

    @Test fun buildNumberFromTag() {
        assertEquals(11, GitHubReleases.buildNumber("build-11"))
        assertNull(GitHubReleases.buildNumber("v1.0"))
        assertNull(GitHubReleases.buildNumber("build-11-rc"))
    }

    @Test fun oldReleaseNotesAreOnlyBoilerplate() {
        assertNull(GitHubReleases.releaseNotes("Built from 6eef2d4. Install: https://github.com/jh-wu/tubetv/releases/latest/download/tubetv.apk"))
    }

    @Test fun rejectsReleaseWithoutApkOrTag() {
        assertNull(GitHubReleases.parse("""{"tag_name":"build-3","assets":[]}"""))
        assertNull(GitHubReleases.parse("""{"tag_name":"latest","assets":[{"name":"tubetv.apk","browser_download_url":"https://x/y.apk"}]}"""))
        assertNull(GitHubReleases.parse("""{"message":"Not Found"}"""))
        assertNull(GitHubReleases.parse("<html>"))
    }
}
