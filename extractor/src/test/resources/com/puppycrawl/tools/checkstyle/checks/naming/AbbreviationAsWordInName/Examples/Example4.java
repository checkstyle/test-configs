/*xml
<module name="Checker">
  <module name="TreeWalker">
    <module name="AbbreviationAsWordInName">
      <property name="allowedAbbreviationLength" value="1"/>
      <property name="allowedAbbreviations" value="CSV"/>
      <property name="ignoreStatic" value="true"/>
      <property name="tokens" value="VARIABLE_DEF"/>
    </module>
  </module>
</module>


*/

package com.puppycrawl.tools.checkstyle.checks.naming.abbreviationaswordinname;

// xdoc section -- start
class Example4 {
}
// xdoc section -- end