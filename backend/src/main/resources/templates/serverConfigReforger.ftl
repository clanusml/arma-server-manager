<#-- @ftlvariable name="" type="cz.forgottenempire.servermanager.serverinstance.entities.ReforgerServer" -->
{
  "bindAddress": "",
  "bindPort": ${port},
  "publicAddress": "",
  "publicPort": ${port},
  "a2s": {
    "address": "0.0.0.0",
    "port": ${queryPort}
  },
  "game": {
    "name": "${name!}",
    "password": "${password!}",
    "passwordAdmin": "${adminPassword!}",
    "scenarioId": "${scenarioId!}",
    "maxPlayers": ${maxPlayers},
    "visible": true,
    "crossPlatform": ${crossPlatform?then('true', 'false')},
    "supportedPlatforms": [
<#if supportedPlatforms?? && supportedPlatforms?size gt 0>
  <#list supportedPlatforms as platform>
      "${platform}"<#sep>,</#sep>
  </#list>
<#else>
      "PLATFORM_PC"
</#if>
    ],
<#if admins?? && admins?size gt 0>
    "admins": [
  <#list admins as admin>
      "${admin}"<#sep>,</#sep>
  </#list>
    ],
</#if>
    "gameProperties": {
      "serverMaxViewDistance": ${serverMaxViewDistance?c},
      "serverMinGrassDistance": ${serverMinGrassDistance?c},
      "networkViewDistance": ${networkViewDistance?c},
      "disableThirdPerson": ${thirdPersonViewEnabled?then('false', 'true')},
      "fastValidation": ${fastValidation?then('true', 'false')},
      "battlEye": ${battlEye?then('true', 'false')},
      "VONDisableUI": true,
      "VONDisableDirectSpeechUI": true
    },
    "mods": [
<#list activeMods as mod>
      {
        "modId": "${mod.id}",
        "name": "${mod.name}"
      }<#sep>,</#sep>
</#list>
    ]
  }
}