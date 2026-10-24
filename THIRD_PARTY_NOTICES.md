# Third-party notices

SkyCrypt PV's own code is MIT-licensed (see `LICENSE`). It builds on these projects:

| Project | How it's used | License |
| --- | --- | --- |
| [SkyCrypt](https://github.com/SkyCryptWebsite/SkyCrypt-Frontend) | The website shown in the viewer (sky.shiiyu.moe). **Not bundled**: the mod only loads the public site, like a browser. Unofficial; not affiliated with the SkyCrypt team. | AGPLv3 (site code), MIT (brand assets) |
| [MCEF Modern](https://github.com/DimasKama/mcef-modern) by DimasKama | Embedded browser library, **bundled unmodified** inside the mod jar (`META-INF/jars/`). Its full license ships with it as `LICENSE_mcef-modern`, and you can replace it with any compatible MCEF Modern build. | LGPL-2.1 |
| [java-cef (JCEF)](https://github.com/chromiumembedded/java-cef) and [jcefmaven](https://github.com/jcefmaven/jcefmaven) | Bundled inside MCEF Modern. | BSD-3-Clause / Apache-2.0 |
| [Chromium Embedded Framework](https://bitbucket.org/chromiumembedded/cef) and [Chromium](https://www.chromium.org/) | **Downloaded at first launch** by jcefmaven (from the jcefbuild GitHub releases) into `config/mcef-modern/`. Not part of the mod file. | BSD-3-Clause and others (see the downloaded `LICENSE.txt` / Chromium credits) |
| [Fabric API](https://github.com/FabricMC/fabric) | Required dependency, not bundled. | Apache-2.0 |
| [Mod Menu](https://github.com/TerraformersMC/ModMenu) | Optional integration, not bundled. | MIT |

Hypixel and SkyBlock are trademarks of Hypixel Inc. This project is not affiliated with Hypixel or Mojang.
