/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class Configurer {

  protected static final Logger log = LoggerFactory.getLogger(Configurer)

  protected final Project project
  protected final String moduleName
  protected final Config defaultConfig
  protected String eclipseVersion

  Configurer(Project project, String moduleName) {

    this.project = project
    this.moduleName = moduleName
    this.defaultConfig = new ConfigReader().readFromResource('defaultConfig.groovy')
  }

  protected void afterEvaluate(Closure closure) {
    project.afterEvaluate(closure)
  }

  void apply() {
    configure()
    afterEvaluate(this.&postConfigure)
  }

  private void applyModuleConfig(Closure closure) {

    def applyConfigs = { Config config ->
      EclipseVersionConfig versionConfig = config.versionConfigs[eclipseVersion]
      if(versionConfig != null) {
        if(versionConfig.eclipseMavenGroup != null)
          project.ext.eclipseMavenGroup = versionConfig.eclipseMavenGroup
        EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
        if(moduleConfig) {
          moduleConfig.properties.each { key, value ->
            if(value instanceof Collection)
              value.each { item ->
                if(item instanceof Closure && item.delegate != PlatformConfig) {
                  item.delegate = PlatformConfig
                  item.resolveStrategy = Closure.DELEGATE_FIRST
                }
              }
          }
          closure(moduleConfig)
        }
      }
    }

    applyConfigs(defaultConfig)

    ProjectUtils.collectWithAllAncestors(project).each { Project p ->
      Config config = p.extensions.findByName('wuff')
      if(config)
        applyConfigs(config)
    }
  }

  protected void applyPlugins() {
    project.apply plugin: 'osgi'
  }

  protected void configure() {

    applyPlugins()
    createExtensions()

    if(project.hasProperty('eclipseVersion'))
      // project properties are inherently hierarchical, so parent's eclipseVersion will be inherited
      eclipseVersion = project.eclipseVersion
    else {
      Project p = ProjectUtils.findUpAncestorChain(project, { it.extensions.findByName('wuff')?.defaultEclipseVersion != null })
      eclipseVersion = p != null ? p.wuff.defaultEclipseVersion : defaultConfig.defaultEclipseVersion
      if(eclipseVersion == null)
        eclipseVersion = defaultConfig.defaultEclipseVersion
    }

    project.wuff.defaultEclipseVersion = eclipseVersion

    project.configurations {
      privateLib
      compile.extendsFrom privateLib
    }

    applyModuleConfig { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.configure)
        closure(project)
    }
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTasks() {
    // by default there are no tasks
  }

  protected void createExtensions() {
    project.extensions.create('wuff', Config)
  }

  protected void postConfigure() {
    if(project.version == 'unspecified')
      project.version = '1.0.0.0'
    applyModuleConfig { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.postConfigure)
        closure(project)
    }
    configureTasks()
    configureProducts()
  }
}