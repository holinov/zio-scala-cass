# ScalaCass
### Light Cassandra wrapper that makes retrieval from Rows a little easier

usage is simple: `import com.weather.scalacass._, ScalaCass._` and you're on your way  
```scala
import com.weather.scalacass._, ScalaCass._
r: Row = getARow()
val myStr: String = r.as[String]("mystr")
val myMap: Map[String, Long] = r.as[Map[String, Long]]("mymap")
val myBoolOpt: Option[Boolean] = r.getAs[Boolean]("mybool")
val myBlobOpt: Option[Array[Byte]] = r.getAs[Array[Byte]]("myblob")
```
etc

and with case classes:
```scala
case class Person(name: String, age: Int, job: Option[String])
val person = r.as[Person]
```

### Getting ScalaCass
you can find it on bintray. Currently only supports **scala 2.11**  
**sbt**
```scala
resolvers += Resolver.jcenterRepo
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.1"
```
**maven**  
```xml
<dependency>
  <groupId>com.github.thurstonsand</groupId>
  <artifactId>scalacass_2.11</artifactId>
  <version>0.1</version>
  <type>pom</type>
</dependency>
```

### Performance
performance is pretty decent.

Measurements taken by extracting a String/Case Class 10,000 times with [Thyme](https://github.com/Ichoran/thyme)  
compare the implementation of `as`:
```scala
// with scala-cass
row.as[String]("str")
row.getAs[String]("str")
// native
if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str"))
if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str")) else None)
```

|           |   as   |  getAs |
|:---------:|:------:|:------:|
| ScalaCass | 6.92us | 6.71us |
|   Native  | 6.88us | 7.80us |
ScalaCass is 99.392% the speed of native for `as`, 106.209% the speed of native for `getAs`  
  
compare the implementation of `as` for a case class:
```scala
case class Strings(str1: String, str2: String, str3: String, str4: Option[String])
// with scala-cass
row.as[Strings]
row.getAs[Strings]
// native as
def get(name: String) = if (row.isNull("str")) throw new IllegalArgumentException("") else row.getString("str")
Strings(get("str"), get("str2"), get("str3"), if (row.getColumnDefinitions.contains("str") && !row.isNull("str")) Some(row.getString("str"))
// native getAs
def ga(name: String) = if (row.getColumnDefinitions.contains(name) && !row.isNull(name)) Some(row.getString(name)) else None
    def getAs = for {
      s1 <- ga("str")
      s2 <- ga("str2")
      s3 <- ga("str3")
      s4  = ga("str4")
    } yield Strings(s1, s2, s3, s4)
```
|                             |   as   |  getAs |
|:---------------------------:|:------:|:------:|
|          ScalaCass          | 68.1us | 65.7us |
| ScalaCass w/ cachedImplicit | 39.3us | 39.1us |
|            Native           | 30.5us | 36.5us |     
ScalaCass alone is 44.844% the speed of native for `as`, 55.557% the speed of native for `getAs`  
ScalaCass w/ cachedImplicit is 77.664% the speed of native for `as`, 93.372% the speed of native for `getAs`

### Session utilities

There are also utility functions that work with Cassandra Sessions:
```scala
implicit val s: Session = someCassSession
val ss = new ScalaSession("mykeyspace") // the session can be picked up implicitly
case class MyTable(str: String, i: Option[Int])

// name of table, number of partition keys (left-to-right), number of clustering keys (left-to-right)
ss.createTable[MyTable]("mytable", 1, 0)
ss.insert("mytable", MyTable("a string", Some(1234)))
ss.selectOne("mytable", MyTable("a string", None)) // returns Some(MyTable("a string", Some(1234)))
ss.selectOneRaw[MyTable]("SELECT * FROM mykeyspace.mytable WHERE str=?", "a string") // returns Some(MyTable("a string", Some(1234)))
ss.delete("mytable", MyTable("a string", None))
ss.selectOne("mytable", MyTable("a string", None)) // returns None
```
* all case classes should be modeled left-to-right with partition keys -> clustering keys -> remaining columns
* all functions have an async variant that returns a scala `Future[ResultSet]`
* there is no need to convert types to AnyRef variants. That is all handled by ScalaCass (with the exception of `Raw` functions)
* all queries are prepared and cached in ScalaSession
* undefined behavior will throw whatever error the Java driver does unless otherwise explicitly mentioned

`createTable` can take an additional parameter tableProperties: String, containing all text after the WITH clause, eg:
```scala
case class MyTable(str: String, i: Option[Int])
// generates "CREATE TABLE mykeyspace.mytable (str varchar, i int, PRIMARY KEY ((str))) WITH compression = { 'sstable_compression' : 'DeflateCompressor', 'chunk_length_kb' : 64 }"
ss.createTable[MyTable]("mytable", 1, 0, "compression = { 'sstable_compression' : 'DeflateCompressor', 'chunk_length_kb' : 64 }")
```
* an error is thrown if number of partition keys is set to 0 or number of partition and clustering keys is greater than the number of fields in the case class  
* createTable is intended to be used primarily for testing frameworks.

`insert` or `insertAsync`
```scala
case class MyTable(str: String, i: Option[Int])
// generates """INSERT INTO mykeyspace.mytable (str, i) VALUES ("asdf", 1234)"""
ss.insert("mytable", MyTable("asdf", Some(1234)))
// generates """INSERT INTO mykeyspace.mytable (str) VALUES ("asdf")"""
ss.insertAsync("mytable", MyTable("asdf2", None)).unsafePerformSync
```
* nulls are not written into Cassandra for None case
* queries are prepared and cached so they only need to be generated once

`select`, `selectAsync`, `selectOne`, or `selectOneAsync`  
can take an additional parameter includeColumns that specifies left-to-right how many fields to include in select query, otherwise use full primary key  
\* NOTE: if includeColumns exceeds the number of keys in the primary key, ALLOW FILTERING is added to the query
```scala
case class MyTable(str: String, i: Option[Int], otherStr: Str)
ss.createTable[MyTable]("mytable", 1, 0)

ss.insert("mytable", MyTable("asdf", None, "otherasdf")
// generates """SELECT * FROM mykeyspace.mytable WHERE str="asdf""""
ss.selectOne("mytable", MyTable("asdf", None, "junkField"), 1) // returns Some(MyTable("asdf", None, "otherasdf"))
```

if a value is None, it is removed from the query  
```scala
ss.insert("mytable", MyTable("asdf2", None, "otherasdf")
// generates """SELECT * FROM mykeyspace.mytable WHERE str="asdf2" AND otherstr="otherasdf" ALLOW FILTERING"""
ss.select("mytable", MyTable("asdf2", None, "otherasdf"), 3) // returns Iterator(MyTable("asdf2", None, "otherasdf"))
```

if numColumns includes a field that is None, it counts as a column, but is not ultimately used in the query
```scala
ss.insert("mytable", MyTable("asdf3", None, "otherasdf")
// generates """SELECT * FROM mykeyspace.mytable WHERE str="asdf2""""
ss.selectOne("mytable", MyTable("asdf3", None, "junkField"), 2) // returns Some(MyTable("asdf3", None, "otherasdf") 
```

`selectRaw`, `selectRawAsync`, `selectOneRaw`, or `selectOneRawAsync`  
simply takes the query string and AnyRef args and executes, with the benefit of prepared statement caching
```scala
case class MyTable(str: String, i: Option[Int], otherStr: Str)
ss.createTable[MyTable]("mytable", 1, 0)

ss.insert("mytable", MyTable("asdf", None, "otherasdf")
ss.selectOneRaw[MyTable]("SELECT * FROM mykeyspace.mytable WHERE str=?", "asdf") // returns Some(MyTable("asdf", None, "otherasdf"))
```

`delete` or `deleteAsync`  
can take an additional parameter includeColumns that specifies left-to-right how many fields to include in delete query, otherwise use full primary key
```scala
case class MyTable(str: String, str2: Option[String], i: Option[Int])
ss.createTable[MyTable]("mytable", 1, 1)

ss.insert("mytable", MyTable("asdf", Some("zxcv"), Some(1234))
ss.insert("mytable", MyTable("asdf", Some("zxcv2"), None)
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf" AND str2="zxcv"""
ss.delete("mytable", MyTable("asdf", Some("zxcv"), None))
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator(MyTable("asdf", Some("zxcv2"), Some(1234)))

ss.insert("mytable", MyTable("asdf", Some("zxcv"), Some(1234))
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf""""
ss.delete("mytable", MyTable("asdf", Some("zxcv"), None), 1)
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator.empty[MyTable]
```

if a value is None, it is removed from the query
```scala
ss.insert("mytable", MyTable("asdf", Some("zxcv"), None)
ss.insert("mytable", MyTable("asdf", Some("zxcv2"), None)
// generates """DELETE * FROM mykeyspace.mytable WHERE str="asdf""""
ss.delete("mytable", MyTable("asdf", None, None), 2)
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator.empty[MyTable]
```

`insertRaw`, `insertRawAsync`, `deleteRaw`, or `deleteRawAsync`  
as with `selectRaw`, uses direct query string with benefits of caching
```scala
ss.insertRaw("INSERT INTO mykeyspace.mytable (str str2) VALUES (?,?)", "asdf", "zxcv")
ss.insertRaw("INSERT INTO mykeyspace.mytable (str str2) VALUES (?,?)", "asdf", "zxcv2")
ss.deleteRaw("DELETE * FROM mykeyspace.mytable WHERE str=? AND str2=?", "asdf", "zxcv")
ss.select("mytable", MyTable("asdf", None, None)) // returns Iterator(MyTable("asdf", Some("zxcv2"), Some(1234)))
```

the session utilities are experimental, and suggestions are welcome on a better syntax
