import importlib.util,json,pathlib,unittest
HERE=pathlib.Path(__file__).parent
spec=importlib.util.spec_from_file_location('build_item_acquisition',HERE/'build-item-acquisition.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class AcquisitionTests(unittest.TestCase):
    def test_real_export_conditions_survive_but_assumed_and_derived_rates_do_not(self):
        rows=json.loads((HERE/'fixtures/item-acquisition/wiki-export-entries.json').read_text())['entries']
        items={str(row['item_id']):{'name':row['name']} for row in rows}
        result=module.build({'tables':{'fixture':{'wiki_page':'Source','entries':rows}}},{},items)
        byid={item['itemId']:item for item in result['items']}
        for row in rows:
            source=byid[row['item_id']]['sources'][0]
            if row.get('assumed_rarity'):self.assertNotIn('Rarity:',source['details'])
            elif row.get('rarity_raw'):self.assertIn(row['rarity_raw'],source['details'])
            for note in row.get('notes',[]):self.assertIn(note,source['details'])
    def test_noted_placeholders_and_shared_tables_are_not_invented_as_direct_sources(self):
        result=module.build({'tables':{'x':{'wiki_page':'Boss','entries':[{'item_id':1,'quantity':[1,2],'rarity':.123},{'shared_table':'rareDrop'},{'item_id':2}]}}},{},{'1':{'name':'Ore'},'2':{'name':'Ore','noted':True}})
        self.assertEqual([1],[item['itemId'] for item in result['items']]);self.assertEqual('Quantity: 1–2',result['items'][0]['sources'][0]['details'])
    def test_shop_currency_and_stock_are_kept_without_computing_a_price(self):
        result=module.build({'tables':{}},{'Shop':{'items':[{'id':1,'stock':10,'currency':'Tokkul','restock_time':5}]}},{'1':{'name':'Ore'}})
        self.assertEqual('Stock: 10 · Restock: 5 seconds · Currency: Tokkul',result['items'][0]['sources'][0]['details'])
if __name__=='__main__':unittest.main()
