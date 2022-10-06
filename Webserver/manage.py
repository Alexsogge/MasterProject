import os

from app import db, app
from data_factory import DataFactory
from models import Recording, RecordingCalculations, Participant
import numpy as np
from views import generate_recording_stats
import pandas as pd
import zipfile

import sys
import argparse
import yaml
try:
    import gtk
    enable_clipboard = True
except ModuleNotFoundError:
    enable_clipboard = False

arg_parser = argparse.ArgumentParser(description='Execute manual operations')

arg_parser.add_argument('Action',
                        metavar='action_name',
                        type=str,
                        help='name of action',
                        choices=['create_db', 'calc_characteristics', 'build_personalized_models', 'rerun_tests',
                                 'recreate_stats', 'generate_personalization_config', 'calc_handwashs_per_hour',
                                 'clean_complete_dataset_files'])

arg_parser.add_argument('-p', '--participants',
                        dest='participants',
                        nargs='+',
                        help='specify participants')

arg_parser.add_argument('-b', '--best',
                        dest='use_best',
                        action='store_true',
                        help='use best personalization as base')

arg_parser.add_argument('-f', '--filter',
                        dest='filter',
                        default='alldeepconv_correctbyconvlstm3filter6',
                        help='specify pseudo label filter setting')

arg_parser.add_argument('-ur', '--use_regularization',
                        dest='use_regularization',
                        action='store_true',
                        help='use L2-SP regularization')



args = arg_parser.parse_args()

def create_db():
    with app.app_context():
        db.create_all()


def calc_recording_characteristics():
    with app.app_context():
        print('Start calculation...')
        for recording in Recording.query.all():
            if recording.calculations is None:
                print('Calc characteristics for', recording.get_name())
                try:
                    data_factory = DataFactory(recording, False)
                    variance = data_factory.calc_variance()
                except Exception as e:
                    print('Error while reading file. Set Dummy entry')
                    variance = np.array([0.0, 0.0, 0.0])
                calculations = RecordingCalculations()
                calculations.store_variance(variance)

                recording.calculations = calculations

                db.session.add(calculations)
                db.session.commit()
            else:
                print('skip', recording.get_name())


def build_personalized_models():
    with app.app_context():
        if args.participants:
            observed_participants = args.participants
            observed_participants = [int(part_id) for part_id in observed_participants]
            print('Start personalization for ', observed_participants)
        else:
            observed_participants = None
            print('Start personalization...')

        for participant in Participant.query.filter_by(is_active=True, enable_personalization=True):
            if observed_participants is None or participant.id in observed_participants:
                print(participant.get_name())
                participant.run_personalization(target_filter=args.filter, use_best=args.use_best,
                                                use_regularization=args.use_regularization)


def rerun_tests_on_personalizations():
    with app.app_context():
        if args.participants:
            observed_participants = args.participants
            observed_participants = [int(part_id) for part_id in observed_participants]
            print('Start rerun tests for ', observed_participants)
        else:
            observed_participants = None
            print('Start rerun tests...')

        for participant in Participant.query.filter_by(is_active=True, enable_personalization=True):
            if observed_participants is None or participant.id in observed_participants:
                print(participant.get_name())
                participant.rerun_tests_on_personalization()


def recreate_recording_stats():
    with app.app_context():
        for recording in Recording.query.all():
            if recording.stats is not None:
                db.session.delete(recording.stats)
                rec_stats = generate_recording_stats(recording, db)
                recording.stats = rec_stats
        db.session.commit()

def generate_personalization_config():
    collection_settings = {
                            'default': {'pseudo_filter': 'alldeepconv_correctbyconvlstm3filter6', 'base_on_best': True, 'use_l2_sp': False},
                            'inc': {'pseudo_filter': 'alldeepconv_correctbyconvlstm3filter6', 'base_on_best': False, 'use_l2_sp': False},
                            'l2sp': {'pseudo_filter': 'alldeepconv_correctbyconvlstm3filter6', 'base_on_best': True, 'use_l2_sp': True},
                            'all_noise': {'pseudo_filter': 'allnoise_correctbyconvlstm3filter', 'base_on_best': True, 'use_l2_sp': False},
                            'deepconv2': {'pseudo_filter': 'alldeepconv_correctbyconvlstm2filter6', 'base_on_best': True, 'use_l2_sp': False},
                            'deepconv2_l2sp': {'pseudo_filter': 'alldeepconv_correctbyconvlstm2filter6', 'base_on_best': True, 'use_l2_sp': True},
                            'no_manual': {'pseudo_filter': 'alldeepconv_correctbyconvlstm3filter6', 'base_on_best': True, 'use_l2_sp': False, 'use_manuals': False},
                            'score': {'pseudo_filter': 'allnoise_correctedscore', 'base_on_best': True,
                                          'use_l2_sp': False},
                            # 'deepconv': {'pseudo_filter': 'allnoise_correctbydeepconvfilter', 'base_on_best': True,
                            #               'use_l2_sp': False},
                            # 'fcndae': {'pseudo_filter': 'allnoise_correctbyfcndaefilter', 'base_on_best': True,
                            #               'use_l2_sp': False},
                            # 'lstm': {'pseudo_filter': 'allnoise_correctbyconvlstmfilter', 'base_on_best': True,
                            #            'use_l2_sp': False},
                           }
    # tmp_configs = {'OCDetect_09': {'enforce': True}, 'OCDetect_11': {'enforce': True}, 'OCDetect_13': {'enforce': True}}
    tmp_configs = {}
    with app.app_context():
        new_config = {}
        entries = 0
        for participant in Participant.query.filter_by(enable_personalization=True):
            for setting_name, settings in collection_settings.items():
                new_entry = {'name': participant.get_name() + '_' + setting_name, 'participant': participant.get_name(),
                             'train_sets': participant.get_train_set(), 'test_sets': participant.get_test_set()}
                new_entry.update(settings)
                for tmp_config_key, tmp_config in tmp_configs.items():
                    if tmp_config_key in new_entry['name']:
                        new_entry.update(tmp_config)

                new_config[f'{entries:03d}'] = new_entry
                entries += 1

        yaml_string = yaml.dump(new_config, sort_keys=False, default_flow_style=None)
        print(yaml_string)
        if enable_clipboard:
            clipboard = gtk.clipboard_get()
            clipboard.set_text(yaml_string)
            clipboard.store()

def calc_handwashs_per_hour():
    with app.app_context():
        entries = {}
        for participant in Participant.query.filter_by():
            # print(participant.get_stat_entries().keys())
            if 'recorded hours' not in participant.get_stat_entries() or 'total hand washes' not in participant.get_stat_entries() or len(participant.get_stat_entries()) == 0 or float(participant.get_stat_entries()['total hand washes'][0]) == 0:
                continue
            #print(participant.get_name(), float(participant.get_stat_entries()['recorded hours'][0]), float(participant.get_stat_entries()['total hand washes'][0]), float(participant.get_stat_entries()['recorded hours'][0])/float(participant.get_stat_entries()['total hand washes'][0]))
            false_detections = float(participant.get_stat_entries()['detected hand washes'][0]) - float(participant.get_stat_entries()['evaluated yes'][0])
            entries[participant.get_name()] = float(participant.get_stat_entries()['total hand washes'][0])/float(participant.get_stat_entries()['recorded hours'][0])
            #entries[participant.get_name()] = false_detections

        sorted_entries = {k: v for k, v in sorted(entries.items(), key=lambda item: item[1])}
        for key, val in sorted_entries.items():
            print(key, val)

def clean_complete_dataset_files():
    with app.app_context():
        entries = {}
        for participant in Participant.query.filter_by():
            for recording in participant.recordings:
                path = recording.path
                zip_file = os.path.join(path, DataFactory.complete_dataset_file_name + '.zip')
                if os.path.exists(zip_file):
                    zf = zipfile.ZipFile(zip_file)
                    df = pd.read_csv(zf.open(DataFactory.complete_dataset_file_name + '.csv'), sep='\t')
                    columns = list(df.columns)
                    if 'pseudo null' in columns:
                        target = zipfile.ZipFile(os.path.join(path, DataFactory.complete_dataset_file_name_labeled + '.zip'), 'w', zipfile.ZIP_DEFLATED)
                        target.writestr(DataFactory.complete_dataset_file_name_labeled + '.csv', zf.read(DataFactory.complete_dataset_file_name + '.csv'))
                        target.close()
                        os.remove(zip_file)


if __name__ == '__main__':
    if len(sys.argv) == 1:
        print('usage: manage.py <command>')
    else:
        print('execute:', sys.argv)
        if args.Action == 'create_db':
            create_db()

        if args.Action == 'calc_characteristics':
            calc_recording_characteristics()

        if args.Action == 'build_personalized_models':
            build_personalized_models()

        if args.Action == 'rerun_tests':
            rerun_tests_on_personalizations()

        if args.Action == 'recreate_stats':
            recreate_recording_stats()

        if args.Action == 'generate_personalization_config':
            generate_personalization_config()

        if args.Action == 'calc_handwashs_per_hour':
            calc_handwashs_per_hour()

        if args.Action == 'clean_complete_dataset_files':
            clean_complete_dataset_files()
