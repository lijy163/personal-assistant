import { defineConfig } from "astro/config";
import react from "@astrojs/react";
import tailwind from "@astrojs/tailwind";
export default defineConfig({site:"http://localhost",base:"/rain7",output:"static",integrations:[react(),tailwind({applyBaseStyles:false})]});