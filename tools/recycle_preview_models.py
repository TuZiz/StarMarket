"""Preview-only upward transforms, derived from the installed matching vanilla client assets."""
from pathlib import Path
import copy
import json
import zipfile


def export(root: Path, assets: Path) -> str:
    client = Path('C:/Users/32633/Desktop/leaf/cache/font-pack-minecraft-26.2-client.jar')
    with zipfile.ZipFile(client) as vanilla:
        def gui_transform(model):
            namespace, name = model.split(':', 1)
            data = json.loads(vanilla.read(f'assets/{namespace}/models/{name}.json'))
            if 'gui' in data.get('display', {}):
                return copy.deepcopy(data['display']['gui'])
            parent = data.get('parent', '')
            if not parent or parent.startswith('builtin/'):
                return {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}
            return gui_transform(parent if ':' in parent else 'minecraft:' + parent)

        mapping = []
        for material in ['chest', 'cobblestone', 'oak_log', 'iron_ingot']:
            definition = json.loads(vanilla.read(f'assets/minecraft/items/{material}.json'))
            # Necessary but not sufficient: 26.2 also requires bounds wider/taller than 16px.
            definition['oversized_in_gui'] = True
            models = {}

            def translate(node):
                if isinstance(node, list):
                    for value in node:
                        translate(value)
                elif isinstance(node, dict):
                    kind = node.get('type')
                    field = 'model' if kind == 'minecraft:model' else 'base' if kind == 'minecraft:special' else None
                    if field:
                        original = node[field]
                        target = f'item/preview/{material}_{len(models)}'
                        if original not in models:
                            transform = gui_transform(original)
                            offset = transform.setdefault('translation', [0, 0, 0])
                            offset[1] += 8  # GUI model units equal logical screen pixels at default scale.
                            model = {'parent': original, 'display': {'gui': transform}}
                            dest = assets / f'models/{target}.json'
                            dest.parent.mkdir(parents=True, exist_ok=True)
                            dest.write_text(json.dumps(model, indent=2), encoding='utf-8')
                            models[original] = 'starmarket:' + target
                        node[field] = models[original]
                    for key, value in node.items():
                        if key != field:
                            translate(value)

            translate(definition)
            # GuiItemRenderState.calculateOversizedItemBounds tests SIZE, not whether
            # a translated 16px model extends outside its slot. Add invisible 20px
            # geometry so the client selects OversizedItemRenderer instead of its
            # fixed 16px atlas tile. The original model's size/lighting stays intact.
            definition['model'] = {
                'type': 'minecraft:composite',
                'models': [definition['model'], {
                    'type': 'minecraft:model',
                    'model': 'starmarket:item/preview/render_bounds',
                }],
            }
            dest = assets / f'items/preview/{material}.json'
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(json.dumps(definition, indent=2), encoding='utf-8')
            if material != 'chest':
                mapping.append(f"      {material.upper()}: 'starmarket:preview/{material}'")
        bounds = {
            'textures': {'blank': 'starmarket:item/gui_blank'},
            'display': {'gui': {'translation': [0, 8, 0]}},
            'elements': [{
                'from': [-2, -2, 8], 'to': [18, 18, 8],
                'faces': {'north': {'texture': '#blank', 'uv': [0, 0, 16, 16]}},
            }],
        }
        (assets / 'models/item/preview/render_bounds.json').write_text(
            json.dumps(bounds, indent=2), encoding='utf-8')
    return "    item-models:\n" + '\n'.join(mapping) + '\n'
