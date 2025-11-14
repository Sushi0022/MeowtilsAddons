# Meowtils Rejects

## How to Add Modules

### Step 1: Create Your Module File

Create a new file in the `modules` folder:

```java
package com.github.tewxx.meowtilsaddons.modules;

import wtf.tatp.meowtils.gui.Module;

public class EmptyModule extends Module {
    public EmptyModule() {
        super("EmptyModule", "emptyModuleKey", "emptyModule", Module.Category.Advanced); 
        try { 
            this.tooltip("An empty placeholder module."); 
        } catch (Throwable ignored) {}
    }
}
```

> **Note:** The category name doesn't matter - all registered modules are automatically moved to the `rejects` category.

---

### Step 2: Register Your Module

Add a line for your module in `meowtilsaddons_modules.txt`:

```
com.github.tewxx.meowtilsaddons.modules.EmptyModule | rejects
```

---

### Step 3: Add Config Options

Add the config options to `MixinCfg`:

```java
public boolean emptyModule = false;
public int emptyModuleKey = 0;
```
> **Note:** If you are missing a config option the module won't show at all

---
