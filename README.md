This mod adds a series of features and tools that enhance the Create mod experience.

### **Scrap Bucket**

Available in Andesite and Brass variants.

The Andesite Scrap Bucket destroys any items or fluids inserted into it via logistics components.

The Brass Scrap Bucket additionally extracts EXP Ingots from destroyed items and fluids at a low efficiency. EXP Ingots can be retrieved by sneaking and right-clicking empty-handed, or extracted via logistics components.

Items or filters can be placed in the side filter slot of the Brass Scrap Bucket, or configured via its GUI, to restrict insertion by filter.

Furthermore, when placed beneath a storage container (for either items or fluids), a target stock level for a specific item or fluid can be set in the GUI. When the filtered item's stock (or total stock if no filter is set) in the container above exceeds this value, the excess will be automatically inserted into the Scrap Bucket and destroyed.

![Andesite and Brass Scrap Bucket](https://media.forgecdn.net/attachments/description/null/description_e19646b3-6d2f-432b-aef6-d24921cfefca.png)

### **Network Manager**

The Network Manager allows you to create and manage tags for existing logistics networks, and assign names and icons to these tags.

It also enables quick configuration of the network connections for placed logistics components using existing tags.

![image](https://media.forgecdn.net/attachments/description/null/description_1e25876a-0bc2-48eb-8c0c-6ea2c24cb21b.png)

### **Labeled Redstone Link**

The Labeled Redstone Link functions essentially the same as Create's Redstone Link, but uses text strings as frequencies.

Only Labeled Redstone Links with matching text frequencies can transmit redstone signals. Like the Wireless Redstone Link, frequencies can be copied and pasted using the Clipboard.

Additionally, similar to how prepending # to a logistics network address in the Clipboard allows quick network assignment, prepending @ to a frequency lets players quickly configure the Link's frequency, with shortcut suggestions provided during configuration.

![image](https://media.forgecdn.net/attachments/description/null/description_d0029be3-ada4-47d0-96d8-f436b3a376b2.png)

### **Batch Mechanical Crafter**

A Mechanical Crafter that supports batch crafting. Works the same as a regular Mechanical Crafter, but each slot can hold a full stack of items and crafts them simultaneously. When processing packages with crafting requests, it always needs to be paired with a Batch Repackager.

When processing packages that carry crafting requests, if the amount of materials in a package exceeds the quantity that can be used in a single batch craft, the package will remain in the packager to wait for the next batch of crafting.

![image](https://media.forgecdn.net/attachments/description/1580762/description_c11c5ca3-68f2-4845-8b96-ecb352ef61d3.png)

### **Batch Repackager**

Functions the same as a Repackager, processing packages from Batch Crafting requests into a format the Batch Mechanical Crafter can accept. Supports multiple package input/output when raw material quantity exceeds a single package capacity, and also supports packages where a single request contains multiple recipes.

For raw material packages carrying crafting requests, the Batch Repackager will always output packages containing integer multiples of the materials required for a single craft of the corresponding recipe.

To make this easier to understand, assume a request uses 128 Iron Ingots and 768 Planks to craft 128 Shields:

The Input: There are three raw material packages: one containing 2x64 Iron Ingots, one with 9x64 Planks, and one with 3x64 Planks.

The Output: In this scenario, the Repackager will output them as follows:

First Package: Contains 74 Iron Ingots (64+10) and 444 Planks (6x64+60). It fills the package as much as possible while ensuring the output always contains integer multiples of the required crafting materials.

Second Package: Contains 54 Iron Ingots and 324 Planks (5x64+4).

Multiple Recipes: In cases where a single request contains multiple recipes (typically when manually requested via the Storage Manager), it will process the recipes sequentially and output the required raw material packages for each specific recipe.

![image](https://media.forgecdn.net/attachments/description/1580762/description_30e28858-e242-4b65-9c77-adcb12e06cbf.png)

### **Factory Panel Demand Request Mode**

A new button has been added to the recipe mode of the Factory Panel to toggle Demand Request Mode on or off for that panel. This button is only visible when the panel is connected to other Factory Panels.

In Demand Request Mode, the recipe configured in the panel is treated as a single-craft recipe, which utilizes the minimum amount of materials required to satisfy the crafting conditions.

If the monitored materials for the panel fall below the set threshold, it will attempt to request all raw materials needed to fill the material gap to the specified address at once, based on the configured minimum recipe.

Building on this, if there is an insufficient supply of raw materials in the network, the panel will attempt to send out the maximum possible batches of raw materials to fill the gap as much as it can.

To make this easier to understand, assume the panel is set to produce 1 Stripped Oak Log using 1 Oak Log, Demand Request Mode is enabled, and the monitored quantity for Stripped Oak Logs is configured to 64:

When the amount of Stripped Oak Logs in the logistics network is 20, the panel will attempt to request 44 Oak Logs to the specified address at once, based on the set minimum recipe.

If there are only 30 Oak Logs left in the network, it will request all 30 Oak Logs to the specified address at once, and the promised amount will correspondingly be exactly 30.

![image](https://media.forgecdn.net/attachments/description/1580762/description_0c271351-14a6-4930-bba4-1004c76a3706.png)

### **Quick Unpacking**

Allows players to right-click Packages inside inventory or any container GUI to quickly unpack them.

The contents will first fill the current container or inventory, then the hotbar. If there is insufficient space, items will be dropped at the player's feet.