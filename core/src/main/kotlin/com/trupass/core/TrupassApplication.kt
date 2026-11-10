package com.trupass.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TrupassApplication

fun main(args: Array<String>) {
    runApplication<TrupassApplication>(*args)
}
