#!/usr/bin/env python3
# 将 tiler 下载的各层级瓦片软链接到 mapfile 统一目录
# vec_w -> mapfile/tianditu_vec/vec_guizhou-z9-17/
# cva_w -> mapfile/tianditu_cva/cva_guizhou-z9-17/

import os, re, glob, sys

TILER_DIR = "/Users/my/coding/bank/tiler"
MAPFILE_DIR = "/Users/my/coding/bank/wangdianxuanzhi/mapfile"

def link_tiles(src_base, dst_dir, name):
    os.makedirs(dst_dir, exist_ok=True)
    print(f"处理 {name} ...")

    total_linked = 0
    # 扫描 tiler 输出目录中所有子目录
    for d in sorted(glob.glob(os.path.join(src_base, "*"))):
        if not os.path.isdir(d):
            continue
        dirname = os.path.basename(d)
        # 提取层级数字范围, 如 vec_guizhou-z13-13 -> {13}, vec_guizhou-z9-12 -> {9,10,11,12}
        m = re.search(r'z(\d+)-(\d+)', dirname)
        if not m:
            continue
        z_start, z_end = int(m.group(1)), int(m.group(2))
        allowed_zooms = set(range(z_start, z_end + 1))

        # 先检查是否有瓦片
        png_files = list(glob.glob(os.path.join(d, "**", "*.png"), recursive=True))
        if not png_files:
            print(f"   跳过 {dirname} (空)")
            continue

        linked = 0
        for png_path in png_files:
            rel = os.path.relpath(png_path, d)  # e.g. 13/407/216.png
            parts = rel.split(os.sep)
            if len(parts) < 3:
                continue
            z, x, y_file = parts[0], parts[1], parts[-1]
            try:
                z_int = int(z)
            except ValueError:
                continue
            if z_int not in allowed_zooms:
                continue  # 目录名和实际层级不一致的跳过

            dst_path = os.path.join(dst_dir, z, x, y_file)
            if not os.path.exists(dst_path):
                os.makedirs(os.path.dirname(dst_path), exist_ok=True)
                os.symlink(png_path, dst_path)
                linked += 1
                total_linked += 1

        print(f"    {dirname} -> {linked} 个新软链接")

    # 统计总数
    total = sum(len(files) for _, _, files in os.walk(dst_dir))
    print(f"  {name} 总计: {total} 张瓦片\n")
    return total_linked, total

print("=" * 40)
print(" 天地图瓦片 -> mapfile 软链接脚本")
print("=" * 40)
print()

total_v, count_v = link_tiles(
    os.path.join(TILER_DIR, "tianditu_street_output"),
    os.path.join(MAPFILE_DIR, "tianditu_vec", "vec_guizhou-z9-17"),
    "vec_w (街道底图)"
)

total_c, count_c = link_tiles(
    os.path.join(TILER_DIR, "tianditu_cva_output"),
    os.path.join(MAPFILE_DIR, "tianditu_cva", "cva_guizhou-z9-17"),
    "cva_w (标注层/POI)"
)

print(f"完成！共创建 {total_v + total_c} 个软链接")
print(f"vec_w: {count_v} 张, cva_w: {count_c} 张")
