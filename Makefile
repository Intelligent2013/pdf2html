#!make
ifeq ($(OS),Windows_NT)
#SHELL := cmd
else
SHELL ?= /bin/bash
endif

JAR_VERSION := $(shell mvn -q -Dexec.executable="echo" -Dexec.args='$${project.version}' --non-recursive exec:exec -DforceStdout)
JAR_FILE := pdf2html-$(JAR_VERSION).jar


ifeq ($(OS),Windows_NT)
  CMD_AND = &
else
  CMD_AND = ;
endif

all: target/$(JAR_FILE)


target/$(JAR_FILE):
	mvn clean package

clean:
	mvn clean


.PHONY: all clean test deploy version publish
