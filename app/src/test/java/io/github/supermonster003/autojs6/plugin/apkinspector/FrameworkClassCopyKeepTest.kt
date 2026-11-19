package io.github.supermonster003.autojs6.plugin.apkinspector

import com.reandroid.arsc.chunk.TableBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * ARSCLib ships its own copies of framework classes (`android.util.AttributeSet`,
 * `android.content.res.XmlResourceParser`, the `org.xmlpull.v1` API). R8 treats such copies as program
 * classes; an obfuscated copy is no longer the framework type for the code that uses it. AppCompat
 * stopped implementing `LayoutInflater.Factory2` (AbstractMethodError in release builds 1.1.0 - 1.2.4),
 * and its Toolbar called `XmlPullParser.getEventType()` on an interface the framework parser does not
 * implement (IncompatibleClassChangeError). Every class outside ARSCLib's own `com.reandroid` package
 * needs a `-keep` rule in `proguard-rules.pro`, by name or by a `package.**` wildcard.
 */
class FrameworkClassCopyKeepTest {

    @Test
    fun everyFrameworkClassCopyInArscLibKeepsItsNameAndMembers() {
        val copies = frameworkClassCopies()
        assertTrue("ARSCLib no longer ships framework copies; revisit the keep rules", copies.isNotEmpty())
        val kept = Files.readAllLines(projectFile("proguard-rules.pro"))
            .mapNotNull { line -> KEEP_RULE.matchEntire(line.trim())?.groupValues?.get(1) }
        val missing = copies.filterNot { name -> kept.any { pattern -> covers(pattern, name) } }
        assertEquals("Framework class copies without a keep rule in proguard-rules.pro", emptyList<String>(), missing)
    }

    @Test
    fun theKnownCopiesAreStillTheOnesArscLibShips() {
        assertEquals(
            listOf(
                "android.content.res.XmlResourceParser",
                "android.util.AttributeSet",
                "org.xmlpull.v1.XmlPullParser",
                "org.xmlpull.v1.XmlPullParserException",
                "org.xmlpull.v1.XmlPullParserFactory",
                "org.xmlpull.v1.XmlSerializer",
            ),
            frameworkClassCopies(),
        )
    }

    private fun frameworkClassCopies(): List<String> {
        val location = checkNotNull(TableBlock::class.java.protectionDomain?.codeSource?.location) { "ARSCLib has no code source" }
        val jar = File(location.toURI())
        assertTrue("ARSCLib is not a jar on the test class path: $jar", jar.isFile)
        return ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .map { entry -> entry.name }
                .filter { name -> name.endsWith(".class") && !name.startsWith("com/reandroid/") && '$' !in name && !name.startsWith("META-INF/") }
                .map { name -> name.removeSuffix(".class").replace('/', '.') }
                .sorted()
                .toList()
        }
    }

    /** `a.b.C` covers `a.b.C`, `a.b.*` covers the classes of `a.b`, `a.b.**` also those of its subpackages. */
    private fun covers(pattern: String, name: String): Boolean = when {
        pattern.endsWith(".**") -> name.startsWith(pattern.removeSuffix("**"))
        pattern.endsWith(".*") -> name.startsWith(pattern.removeSuffix("*")) && '.' !in name.removePrefix(pattern.removeSuffix("*"))
        else -> pattern == name
    }

    private fun projectFile(moduleRelativePath: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return listOf(workingDirectory.resolve(moduleRelativePath), workingDirectory.resolve("app").resolve(moduleRelativePath))
            .firstOrNull(Files::exists)
            ?: error("Project file not found: $moduleRelativePath from $workingDirectory")
    }

    private companion object {
        val KEEP_RULE = Regex("""-keep class (\S+) \{ \*; }""")
    }
}
