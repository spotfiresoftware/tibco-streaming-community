# Escaped Identifier Mapper Operator

This project provides a Spotfire StreamBase Operator that simply maps its input to its output, but renaming any field name that is escaped (those with "#...") according to a simple prefix configured in the properties. 


# Gotchas

It doesn't handle fields of type FUNCTION, and has no special processing for CAPTURE fields


# Changelog

1.0		Initial version
