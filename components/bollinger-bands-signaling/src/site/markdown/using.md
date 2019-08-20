# Using

This component is available via maven.  Include in your pom.xml file the following :-

```
    <project>
        ...
        <dependencies>
            <dependency>
                <groupId>com.tibco.sb.compexch</groupId>
                <artifactId>BollingerBandsSignaling</artifactId>
                <type>ep-eventflow-fragment</type>
            </dependency>
            ...
        </dependencies>
        ...
        <dependencyManagement>
            <dependencies>
                <dependency>
                     <groupId>com.tibco.sb.compexch</groupId>
                     <artifactId>BollingerBandsSignaling</artifactId>
                     <version>1.0.0</version>
                 </dependency>
                 ...
             </dependencies>
        </dependencyManagement>
        ...
    </project>
```